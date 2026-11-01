package com.homemedguard.service.rule.core;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.homemedguard.service.rule.RuleModule;
import com.homemedguard.service.rule.entity.ComponentAliasEntity;
import com.homemedguard.service.rule.entity.ComponentDictEntity;
import com.homemedguard.service.rule.entity.ComponentLimitEntity;
import com.homemedguard.service.rule.entity.ContraindicationRuleEntity;
import com.homemedguard.service.rule.entity.DrugComponentEntity;
import com.homemedguard.service.rule.entity.InteractionRuleEntity;
import com.homemedguard.service.rule.entity.SpacingRuleEntity;
import com.homemedguard.service.rule.mapper.ComponentAliasMapper;
import com.homemedguard.service.rule.mapper.ComponentDictMapper;
import com.homemedguard.service.rule.mapper.ComponentLimitMapper;
import com.homemedguard.service.rule.mapper.ContraindicationRuleMapper;
import com.homemedguard.service.rule.mapper.DrugComponentMapper;
import com.homemedguard.service.rule.mapper.InteractionRuleMapper;
import com.homemedguard.service.rule.mapper.SpacingRuleMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * 把"已发布且已生效"的规则连同字典/别名/上限一次性装成不可变快照，缓存 10 分钟。
 * 整体装载而不是逐条查缓存，是为了让一次评估内部看到的规则集版本一致，
 * 不会出现前半段用 v3、后半段用 v4 的分裂结果。
 */
@RuleModule
public class DbRuleSetProvider implements RuleSetProvider {

    private static final String PUBLISHED = "PUBLISHED";

    private final ComponentDictMapper componentDictMapper;
    private final ComponentAliasMapper componentAliasMapper;
    private final DrugComponentMapper drugComponentMapper;
    private final ComponentLimitMapper componentLimitMapper;
    private final InteractionRuleMapper interactionRuleMapper;
    private final ContraindicationRuleMapper contraindicationRuleMapper;
    private final SpacingRuleMapper spacingRuleMapper;

    private final Cache<String, RuleSet> cache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    public DbRuleSetProvider(ComponentDictMapper componentDictMapper,
                             ComponentAliasMapper componentAliasMapper,
                             DrugComponentMapper drugComponentMapper,
                             ComponentLimitMapper componentLimitMapper,
                             InteractionRuleMapper interactionRuleMapper,
                             ContraindicationRuleMapper contraindicationRuleMapper,
                             SpacingRuleMapper spacingRuleMapper) {
        this.componentDictMapper = componentDictMapper;
        this.componentAliasMapper = componentAliasMapper;
        this.drugComponentMapper = drugComponentMapper;
        this.componentLimitMapper = componentLimitMapper;
        this.interactionRuleMapper = interactionRuleMapper;
        this.contraindicationRuleMapper = contraindicationRuleMapper;
        this.spacingRuleMapper = spacingRuleMapper;
    }

    @Override
    public RuleSet current() {
        return cache.get("ruleset", key -> load());
    }

    @Override
    public void invalidate() {
        cache.invalidateAll();
    }

    private RuleSet load() {
        List<ComponentDictEntity> dicts = componentDictMapper.selectList(null);
        Map<Long, String> codeById = new HashMap<>();
        Map<String, String> unitByCode = new HashMap<>();
        Map<String, String> aliasToCode = new HashMap<>();
        for (ComponentDictEntity dict : dicts) {
            codeById.put(dict.getId(), dict.getComponentCode());
            unitByCode.put(dict.getComponentCode(), dict.getDoseUnit() == null ? "mg" : dict.getDoseUnit());
            aliasToCode.putIfAbsent(lower(dict.getComponentCode()), dict.getComponentCode());
            aliasToCode.putIfAbsent(lower(dict.getStandardName()), dict.getComponentCode());
        }
        for (ComponentAliasEntity alias : componentAliasMapper.selectList(null)) {
            String code = codeById.get(alias.getComponentId());
            if (code != null) {
                aliasToCode.putIfAbsent(lower(alias.getAlias()), code);
            }
        }

        Map<Long, List<RuleSet.DrugComponentView>> componentsByDrug = new LinkedHashMap<>();
        for (DrugComponentEntity row : drugComponentMapper.selectList(null)) {
            String code = codeById.get(row.getComponentId());
            if (code != null) {
                componentsByDrug.computeIfAbsent(row.getDrugDictId(), key -> new ArrayList<>())
                        .add(new RuleSet.DrugComponentView(code, row.getContentPerForm(),
                                row.getFormUnit(), row.getResolveStatus()));
            }
        }

        Map<String, List<ComponentLimitEntity>> limits = new LinkedHashMap<>();
        for (ComponentLimitEntity row : componentLimitMapper.selectList(null)) {
            String code = codeById.get(row.getComponentId());
            if (code != null) {
                limits.computeIfAbsent(code, key -> new ArrayList<>()).add(row);
            }
        }

        LocalDate today = LocalDate.now();
        List<InteractionRuleEntity> interactions = effective(
                interactionRuleMapper.selectList(Wrappers.<InteractionRuleEntity>lambdaQuery()
                        .eq(InteractionRuleEntity::getStatus, PUBLISHED)),
                row -> row.getEffectiveFrom());
        List<ContraindicationRuleEntity> contraindications = effective(
                contraindicationRuleMapper.selectList(Wrappers.<ContraindicationRuleEntity>lambdaQuery()
                        .eq(ContraindicationRuleEntity::getStatus, PUBLISHED)),
                row -> row.getEffectiveFrom());
        List<SpacingRuleEntity> spacings = effective(
                spacingRuleMapper.selectList(Wrappers.<SpacingRuleEntity>lambdaQuery()
                        .eq(SpacingRuleEntity::getStatus, PUBLISHED)),
                row -> row.getEffectiveFrom());

        List<String> fingerprint = new ArrayList<>();
        interactions.forEach(row -> fingerprint.add(row.getRuleCode() + ":" + row.getVersion()));
        contraindications.forEach(row -> fingerprint.add(row.getRuleCode() + ":" + row.getVersion()));
        spacings.forEach(row -> fingerprint.add(row.getRuleCode() + ":" + row.getVersion()));
        Collections.sort(fingerprint);

        String stamp = today + "#" + fingerprint.size() + "#" + shortHash(String.join(",", fingerprint));
        return new RuleSet(interactions, contraindications, spacings, limits, aliasToCode, unitByCode,
                componentsByDrug, stamp);
    }

    private static <T> List<T> effective(List<T> rows, Function<T, LocalDate> effectiveFrom) {
        LocalDate today = LocalDate.now();
        return rows.stream()
                .filter(row -> {
                    LocalDate from = effectiveFrom.apply(row);
                    return from == null || !from.isAfter(today);
                })
                .toList();
    }

    private static String shortHash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String lower(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}

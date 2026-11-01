package com.homemedguard.service.rule.normalizer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.common.contract.DrugRegisteredEvent;
import com.homemedguard.service.rule.RuleModule;
import com.homemedguard.service.rule.core.RuleSetProvider;
import com.homemedguard.service.rule.entity.ComponentDictEntity;
import com.homemedguard.service.rule.entity.DrugComponentEntity;
import com.homemedguard.service.rule.mapper.ComponentDictMapper;
import com.homemedguard.service.rule.mapper.DrugComponentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ① 新增药品后自动做成分归一化。drug_component 归 ② 所有，① 不写这张表。
 *
 * 商品名多是"布洛芬缓释胶囊"这种带后缀的写法，所以按"名字里包含哪个别名"匹配而不是整串相等；
 * 匹到的成分先记 content=0 + PENDING，等说明书要点录入后由后台改成 CONFIRMED。
 * 一个都匹不上就一行都不写——评估时 ContextBuilder 会因为缺 drug_component 明确报"未归一化、结果不完整"，
 * 比瞎猜一个成分写进去安全得多。
 */
@RuleModule
public class DrugComponentNormalizer {

    private static final Logger log = LoggerFactory.getLogger(DrugComponentNormalizer.class);
    private static final String SEPARATORS = "[\\s,，、/()（）\\[\\]]+";

    private final RuleSetProvider ruleSetProvider;
    private final ComponentDictMapper componentDictMapper;
    private final DrugComponentMapper drugComponentMapper;

    public DrugComponentNormalizer(RuleSetProvider ruleSetProvider,
                                   ComponentDictMapper componentDictMapper,
                                   DrugComponentMapper drugComponentMapper) {
        this.ruleSetProvider = ruleSetProvider;
        this.componentDictMapper = componentDictMapper;
        this.drugComponentMapper = drugComponentMapper;
    }

    /** 与"新增药品"同事务：归一化失败就一起回滚，不留一条没有成分的药品记录。 */
    @EventListener
    @Transactional
    public void onDrugRegistered(DrugRegisteredEvent event) {
        if (event == null || event.drugDictId() == null) {
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        collect(names, event.genericName());
        collect(names, event.tradeName());

        Set<String> matched = new LinkedHashSet<>();
        for (String name : names) {
            for (Map.Entry<String, String> alias : ruleSetProvider.current().aliasToCode().entrySet()) {
                if (alias.getKey().length() >= 2 && name.contains(alias.getKey())) {
                    matched.add(alias.getValue());
                }
            }
        }

        for (String code : matched) {
            insertMapping(event.drugDictId(), code);
        }
        if (matched.isEmpty()) {
            log.info("药品 {}（{}）没有可归一化的成分，评估时会标记为未覆盖", event.drugDictId(), event.genericName());
        } else {
            ruleSetProvider.invalidate();
            log.info("药品 {} 归一化到成分 {}", event.drugDictId(), matched);
        }
    }

    private void insertMapping(Long drugDictId, String componentCode) {
        ComponentDictEntity dict = componentDictMapper.selectOne(Wrappers.<ComponentDictEntity>lambdaQuery()
                .eq(ComponentDictEntity::getComponentCode, componentCode)
                .last("limit 1"));
        if (dict == null) {
            return;
        }
        boolean exists = drugComponentMapper.exists(Wrappers.<DrugComponentEntity>lambdaQuery()
                .eq(DrugComponentEntity::getDrugDictId, drugDictId)
                .eq(DrugComponentEntity::getComponentId, dict.getId()));
        if (exists) {
            return;
        }
        DrugComponentEntity row = new DrugComponentEntity();
        row.setDrugDictId(drugDictId);
        row.setComponentId(dict.getId());
        row.setContentPerForm(BigDecimal.ZERO);
        row.setFormUnit("片");
        row.setResolveStatus("PENDING");
        drugComponentMapper.insert(row);
    }

    private static void collect(Set<String> target, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        for (String token : name.split(SEPARATORS)) {
            String cleaned = token.trim().toLowerCase(Locale.ROOT);
            if (!cleaned.isEmpty()) {
                target.add(cleaned);
            }
        }
    }
}

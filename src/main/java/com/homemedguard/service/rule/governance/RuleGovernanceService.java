package com.homemedguard.service.rule.governance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.service.rule.RuleModule;
import com.homemedguard.service.rule.core.AviatorExpressionEvaluator;
import com.homemedguard.service.rule.core.RuleSetProvider;
import com.homemedguard.service.rule.entity.ContraindicationRuleEntity;
import com.homemedguard.service.rule.entity.InteractionRuleEntity;
import com.homemedguard.service.rule.entity.RulePublishLogEntity;
import com.homemedguard.service.rule.entity.SpacingRuleEntity;
import com.homemedguard.service.rule.mapper.ContraindicationRuleMapper;
import com.homemedguard.service.rule.mapper.InteractionRuleMapper;
import com.homemedguard.service.rule.mapper.RulePublishLogMapper;
import com.homemedguard.service.rule.mapper.SpacingRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * 规则审核发布流：DRAFT →(submit) PENDING →(approve) PUBLISHED →(retire) RETIRED，驳回退回 DRAFT。
 * 三条硬口径：
 * 1. 已发布版本不可原地改，只能开新版本（答辩时"规则可维护又可控"的证据）；
 * 2. 发布必须有来源（sourceRef + sourceDate），表达式必须能编译——宁缺勿错；
 * 3. 同一条 ruleCode 发布新版本时旧的已发布版本自动停用，避免两个版本同时命中。
 *
 * 三张规则表共有治理列（rule_code / version / status / effective_from / source_ref / source_date），
 * 所以这里用列名查询统一处理，不为三个实体写三份一样的流转代码。
 */
@RuleModule
@Service
public class RuleGovernanceService {

    private final InteractionRuleMapper interactionRuleMapper;
    private final ContraindicationRuleMapper contraindicationRuleMapper;
    private final SpacingRuleMapper spacingRuleMapper;
    private final RulePublishLogMapper publishLogMapper;
    private final RuleSetProvider ruleSetProvider;
    private final AviatorExpressionEvaluator expressions;
    private final ObjectMapper objectMapper;

    public RuleGovernanceService(InteractionRuleMapper interactionRuleMapper,
                                 ContraindicationRuleMapper contraindicationRuleMapper,
                                 SpacingRuleMapper spacingRuleMapper,
                                 RulePublishLogMapper publishLogMapper,
                                 RuleSetProvider ruleSetProvider,
                                 AviatorExpressionEvaluator expressions,
                                 ObjectMapper objectMapper) {
        this.interactionRuleMapper = interactionRuleMapper;
        this.contraindicationRuleMapper = contraindicationRuleMapper;
        this.spacingRuleMapper = spacingRuleMapper;
        this.publishLogMapper = publishLogMapper;
        this.ruleSetProvider = ruleSetProvider;
        this.expressions = expressions;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GovernedRule upsertDraft(String table, RuleDraftForm form, Long actorUserId) {
        if (form.ruleCode() == null || form.ruleCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "ruleCode 必填");
        }
        String code = form.ruleCode().trim();
        int version = form.version() == null ? 1 : form.version();

        GovernedRule existing = find(table, code, version);
        if (existing != null && !RuleStatus.DRAFT.equals(existing.getStatus())) {
            throw new BizException(ErrorCode.IDEMPOTENT_CONFLICT,
                    "规则 " + code + " v" + version + " 当前是 " + existing.getStatus() + "，已发布规则不可原地改，请开新版本");
        }

        GovernedRule draft = existing != null ? existing : newDraft(table, code, version);
        apply(draft, table, form);
        draft.setStatus(RuleStatus.DRAFT);

        save(table, draft, existing == null);
        log(table, draft, existing == null ? "CREATE" : "UPDATE", actorUserId, null);
        return draft;
    }

    @Transactional
    public GovernedRule submit(String table, String ruleCode, int version, Long actorUserId) {
        GovernedRule rule = requireRule(table, ruleCode, version);
        requireStatus(rule, RuleStatus.DRAFT, "只有草稿可以提交审核");
        rule.setStatus(RuleStatus.PENDING);
        save(table, rule, false);
        log(table, rule, "SUBMIT", actorUserId, null);
        return rule;
    }

    @Transactional
    public GovernedRule approve(String table, String ruleCode, int version, Long actorUserId, LocalDate effectiveFrom) {
        GovernedRule rule = requireRule(table, ruleCode, version);
        requireStatus(rule, RuleStatus.PENDING, "只有待审核的规则可以发布");
        if (rule.getSourceRef() == null || rule.getSourceRef().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少来源 sourceRef，不允许发布");
        }
        if (rule.getSourceDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少整理日期 sourceDate，不允许发布");
        }

        for (GovernedRule published : listByStatus(table, RuleStatus.PUBLISHED, ruleCode)) {
            published.setStatus(RuleStatus.RETIRED);
            save(table, published, false);
            log(table, published, "SUPERSEDE", actorUserId, "被 " + ruleCode + " v" + version + " 取代");
        }

        rule.setStatus(RuleStatus.PUBLISHED);
        rule.setEffectiveFrom(effectiveFrom == null ? LocalDate.now() : effectiveFrom);
        save(table, rule, false);
        log(table, rule, "APPROVE", actorUserId, null);
        ruleSetProvider.invalidate();
        return rule;
    }

    @Transactional
    public GovernedRule reject(String table, String ruleCode, int version, Long actorUserId, String reason) {
        GovernedRule rule = requireRule(table, ruleCode, version);
        requireStatus(rule, RuleStatus.PENDING, "只有待审核的规则可以驳回");
        requireText(reason, "驳回必须写理由");
        rule.setStatus(RuleStatus.DRAFT);
        save(table, rule, false);
        log(table, rule, "REJECT", actorUserId, reason);
        return rule;
    }

    @Transactional
    public GovernedRule retire(String table, String ruleCode, int version, Long actorUserId, String reason) {
        GovernedRule rule = requireRule(table, ruleCode, version);
        requireStatus(rule, RuleStatus.PUBLISHED, "只有已发布的规则需要停用");
        requireText(reason, "停用必须写理由");
        rule.setStatus(RuleStatus.RETIRED);
        save(table, rule, false);
        log(table, rule, "RETIRE", actorUserId, reason);
        ruleSetProvider.invalidate();
        return rule;
    }

    public List<GovernedRule> listByStatus(String table, String status) {
        return listByStatus(table, status, null);
    }

    public List<RulePublishLogEntity> history(String table, String ruleCode) {
        return publishLogMapper.selectList(new QueryWrapper<RulePublishLogEntity>()
                .eq("rule_table", normalize(table))
                .eq("rule_code", ruleCode)
                .orderByAsc("id"));
    }

    // ---- 内部 --------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<GovernedRule> listByStatus(String table, String status, String ruleCode) {
        QueryWrapper<Object> wrapper = new QueryWrapper<>().eq("status", status);
        if (ruleCode != null) {
            wrapper.eq("rule_code", ruleCode);
        }
        List<GovernedRule> rows = (List<GovernedRule>) (List<?>) rawMapper(table).selectList(wrapper);
        return rows;
    }

    private GovernedRule find(String table, String ruleCode, int version) {
        List<GovernedRule> rows = list(table, new QueryWrapper<>()
                .eq("rule_code", ruleCode).eq("version", version));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private GovernedRule requireRule(String table, String ruleCode, int version) {
        GovernedRule rule = find(table, ruleCode, version);
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "规则不存在：" + normalize(table) + " " + ruleCode + " v" + version);
        }
        return rule;
    }

    @SuppressWarnings("unchecked")
    private List<GovernedRule> list(String table, QueryWrapper<Object> wrapper) {
        return (List<GovernedRule>) (List<?>) rawMapper(table).selectList(wrapper.last("limit 500"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void save(String table, GovernedRule rule, boolean insert) {
        BaseMapper mapper = rawMapper(table);
        if (insert) {
            mapper.insert(rule);
        } else {
            mapper.updateById(rule);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BaseMapper rawMapper(String table) {
        return switch (normalize(table)) {
            case "interaction" -> interactionRuleMapper;
            case "contraindication" -> contraindicationRuleMapper;
            case "spacing" -> spacingRuleMapper;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "未知规则表：" + table);
        };
    }

    private GovernedRule newDraft(String table, String ruleCode, int version) {
        return switch (normalize(table)) {
            case "interaction" -> {
                InteractionRuleEntity entity = new InteractionRuleEntity();
                entity.setRuleCode(ruleCode);
                entity.setVersion(version);
                entity.setTargetType("DRUG");
                yield entity;
            }
            case "contraindication" -> {
                ContraindicationRuleEntity entity = new ContraindicationRuleEntity();
                entity.setRuleCode(ruleCode);
                entity.setVersion(version);
                yield entity;
            }
            case "spacing" -> {
                SpacingRuleEntity entity = new SpacingRuleEntity();
                entity.setRuleCode(ruleCode);
                entity.setVersion(version);
                yield entity;
            }
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "未知规则表：" + table);
        };
    }

    private void apply(GovernedRule rule, String table, RuleDraftForm form) {
        String kind = normalize(table);
        switch (kind) {
            case "interaction" -> {
                InteractionRuleEntity entity = (InteractionRuleEntity) rule;
                requirePair(form.componentA(), form.componentB(), "相互作用规则需要 componentA 与 componentB");
                requireText(form.advice(), "相互作用规则必须有 advice 文案");
                entity.setComponentA(upper(form.componentA()));
                entity.setComponentB(upper(form.componentB()));
                entity.setSeverity(toSeverity(form.severity()));
                entity.setMechanism(trimOrNull(form.mechanism()));
                entity.setAdvice(trim(form.advice()));
                entity.setExpr(toExpr(form.expr()));
            }
            case "contraindication" -> {
                ContraindicationRuleEntity entity = (ContraindicationRuleEntity) rule;
                requirePair(form.component(), form.tagCode(), "禁忌规则需要 component 与 tagCode");
                requireText(form.advice(), "禁忌规则必须有 advice 文案");
                entity.setComponent(upper(form.component()));
                entity.setTagCode(upper(form.tagCode()));
                entity.setSeverity(toSeverity(form.severity()));
                entity.setAdvice(trim(form.advice()));
                entity.setExpr(toExpr(form.expr()));
            }
            case "spacing" -> {
                SpacingRuleEntity entity = (SpacingRuleEntity) rule;
                requirePair(form.componentA(), form.componentB(), "间隔规则需要 componentA 与 componentB");
                if (form.minGapMinutes() == null || form.minGapMinutes() <= 0) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "minGapMinutes 必须大于 0");
                }
                entity.setComponentA(upper(form.componentA()));
                entity.setComponentB(upper(form.componentB()));
                entity.setMinGapMinutes(form.minGapMinutes());
                entity.setReason(trimOrNull(form.mechanism()));
            }
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "未知规则表：" + table);
        }
        // 来源在录入这一步就强制：库里 source_ref/source_date 是 NOT NULL，
        // 一条说不清出处的规则连草稿都不该存在（宁可少覆盖，也不要写错）
        requireText(form.sourceRef(), "规则必须带来源 sourceRef（说明书或指南条目）");
        if (form.sourceDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则必须带整理日期 sourceDate");
        }
        rule.setSourceRef(trim(form.sourceRef()));
        rule.setSourceDate(form.sourceDate());
    }

    private String toExpr(String expr) {
        String cleaned = trimOrNull(expr);
        if (cleaned != null && !expressions.validates(cleaned)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "表达式编译不通过，先修好再存草稿");
        }
        return cleaned;
    }

    private void log(String table, GovernedRule rule, String action, Long actorUserId, String reason) {
        RulePublishLogEntity entry = new RulePublishLogEntity();
        entry.setRuleTable(normalize(table));
        entry.setRuleCode(rule.getRuleCode());
        entry.setRuleVersion(rule.getVersion());
        entry.setAction(action);
        entry.setActorUserId(actorUserId);
        entry.setReason(trimOrNull(reason));
        try {
            entry.setSnapshotJson(objectMapper.writeValueAsString(rule));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            entry.setSnapshotJson("{\"snapshotFailed\":true}");
        }
        publishLogMapper.insert(entry);
    }

    private void requireStatus(GovernedRule rule, String expected, String message) {
        if (!expected.equals(rule.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, message + "（当前状态 " + rule.getStatus() + "）");
        }
    }

    private static String normalize(String table) {
        return table == null ? "" : table.trim().toLowerCase(Locale.ROOT);
    }

    private static String upper(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String toSeverity(String value) {
        String raw = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (raw) {
            case "A", "B", "C" -> raw;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "severity 只能是 A / B / C");
        };
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, message);
        }
    }

    private static void requirePair(String left, String right, String message) {
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, message);
        }
    }

    private static String trim(String value) {
        return value.trim();
    }

    private static String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.homemedguard.service.rule.core;

import com.homemedguard.service.rule.RuleModule;
import com.homemedguard.service.rule.entity.ComponentLimitEntity;
import com.homemedguard.service.rule.entity.ContraindicationRuleEntity;
import com.homemedguard.service.rule.entity.InteractionRuleEntity;
import com.homemedguard.service.rule.entity.SpacingRuleEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 求值主流程：过敏史 → 相互作用 → 禁忌人群 → 重复成分剂量叠加 → 需间隔。
 * 全部只读 {@link RuleSet} 与 {@link RiskContext}，无状态、不碰数据库，因此可以用纯内存规则做表驱动测试。
 *
 * 失败策略（口径很重要，别改）：表达式求值出错 → 记入 failedRuleCodes 且**不出命中**，
 * 由调用方把"本次判定不完整"显示出来；未识别的成分同样算不完整，绝不当成"没有冲突"。
 */
@RuleModule
public class RuleEvaluator {

    private static final BigDecimal SEVERE_RATIO = new BigDecimal("1.5");
    private static final Set<String> TAG_POPULATIONS =
            Set.of("CHILD", "ELDERLY", "PREGNANT", "LACTATING", "HEPATIC_IMPAIRED", "RENAL_IMPAIRED");

    private final AviatorExpressionEvaluator expressions;

    public RuleEvaluator(AviatorExpressionEvaluator expressions) {
        this.expressions = expressions;
    }

    public EvaluationOutcome evaluate(RiskContext ctx, RuleSet rules) {
        List<RiskHit> hits = new ArrayList<>();
        List<String> failedRules = new ArrayList<>();
        List<String> incomplete = new ArrayList<>(ctx.unresolvedComponents());

        hits.addAll(allergyHits(ctx));
        hits.addAll(interactionHits(ctx, rules, failedRules));
        hits.addAll(contraindicationHits(ctx, rules, failedRules));
        hits.addAll(doseLimitHits(ctx, rules, incomplete));
        hits.addAll(spacingHits(ctx, rules));

        hits.sort(Comparator.comparingInt(hit -> hit.severity().ordinal()));
        return new EvaluationOutcome(rules.stamp(), List.copyOf(hits), failedRules, incomplete);
    }

    /** 过敏史比对不依赖规则库覆盖度，是②唯一"零规则也能兜住"的判定。 */
    private List<RiskHit> allergyHits(RiskContext ctx) {
        List<RiskHit> hits = new ArrayList<>();
        for (String component : ctx.components()) {
            if (!ctx.allergyComponents().contains(component)) {
                continue;
            }
            Map<String, String> evidence = new LinkedHashMap<>();
            evidence.put(component, plain(ctx.doseOf(component)) + " mg/d");
            evidence.put("过敏史", "命中");
            hits.add(new RiskHit(RiskRuleKind.CONTRAINDICATION, null, "MEMBER_ALLERGY_HISTORY", null,
                    Severity.A, List.of(component), "既往过敏史",
                    "停用该成分，并告知医生既往过敏记录",
                    "在服成分 " + component + " 出现在成员过敏史中",
                    evidence, "成员档案 family_member_profile.allergy_history", null));
        }
        return hits;
    }

    private List<RiskHit> interactionHits(RiskContext ctx, RuleSet rules, List<String> failedRules) {
        List<RiskHit> hits = new ArrayList<>();
        for (InteractionRuleEntity rule : rules.interactions()) {
            BigDecimal doseA = ctx.doseOf(rule.getComponentA());
            if (doseA.signum() <= 0) {
                continue;
            }
            boolean againstDrug = rule.getTargetType() == null || "DRUG".equalsIgnoreCase(rule.getTargetType());
            BigDecimal doseB = againstDrug ? ctx.doseOf(rule.getComponentB()) : BigDecimal.ZERO;
            boolean pairHit = againstDrug ? doseB.signum() > 0 : ctx.targets().contains(rule.getComponentB());
            if (!pairHit || !gate(rule.getExpr(), rule.getRuleCode(), ctx, doseA, doseB, failedRules)) {
                continue;
            }

            Map<String, String> evidence = new LinkedHashMap<>();
            evidence.put(rule.getComponentA(), plain(doseA) + " mg/d");
            evidence.put(rule.getComponentB(), againstDrug ? plain(doseB) + " mg/d" : "在接触");
            String basis = againstDrug
                    ? "成分 " + rule.getComponentA() + " 与 " + rule.getComponentB() + " 同时在服"
                    : "成分 " + rule.getComponentA() + " 在服，且接触 " + rule.getComponentB();
            hits.add(new RiskHit(RiskRuleKind.INTERACTION, rule.getId(), rule.getRuleCode(), rule.getVersion(),
                    Severity.parse(rule.getSeverity()),
                    againstDrug ? List.of(rule.getComponentA(), rule.getComponentB()) : List.of(rule.getComponentA()),
                    rule.getMechanism(), rule.getAdvice(),
                    AviatorExpressionEvaluator.hasExpression(rule.getExpr())
                            ? basis + "，附加条件成立：" + rule.getExpr() : basis,
                    evidence, rule.getSourceRef(), rule.getSourceDate()));
        }
        return hits;
    }

    private List<RiskHit> contraindicationHits(RiskContext ctx, RuleSet rules, List<String> failedRules) {
        List<RiskHit> hits = new ArrayList<>();
        for (ContraindicationRuleEntity rule : rules.contraindications()) {
            BigDecimal dose = ctx.doseOf(rule.getComponent());
            if (dose.signum() <= 0 || !ctx.tagCodes().contains(rule.getTagCode())) {
                continue;
            }
            if (!gate(rule.getExpr(), rule.getRuleCode(), ctx, dose, BigDecimal.ZERO, failedRules)) {
                continue;
            }
            Map<String, String> evidence = new LinkedHashMap<>();
            evidence.put(rule.getComponent(), plain(dose) + " mg/d");
            evidence.put("人群标签", rule.getTagCode());
            hits.add(new RiskHit(RiskRuleKind.CONTRAINDICATION, rule.getId(), rule.getRuleCode(), rule.getVersion(),
                    Severity.parse(rule.getSeverity()), List.of(rule.getComponent()), null,
                    rule.getAdvice(),
                    "成分 " + rule.getComponent() + " 命中禁忌人群标签 " + rule.getTagCode(),
                    evidence, rule.getSourceRef(), rule.getSourceDate()));
        }
        return hits;
    }

    /**
     * 重复成分叠加：同一种成分可能来自好几盒复方药，先按 mg/d 累加，再比对按人群选出的最严上限。
     * 上限可以是 mg/d 或 mg·kg⁻¹·d⁻¹，两者都配时取更小的那个。
     */
    private List<RiskHit> doseLimitHits(RiskContext ctx, RuleSet rules, List<String> incomplete) {
        List<RiskHit> hits = new ArrayList<>();
        Set<String> noted = new HashSet<>();
        for (String component : ctx.components()) {
            List<ComponentLimitEntity> rows = rules.limits(component);
            if (rows.isEmpty()) {
                if (noted.add(component)) {
                    incomplete.add("成分 " + component + " 库里没有日剂量上限，本次未做超量校验");
                }
                continue;
            }
            Limit ceiling = strictestCeiling(component, rows, ctx, incomplete, noted);
            if (ceiling == null) {
                continue;
            }
            BigDecimal dose = ctx.doseOf(component);
            if (dose.compareTo(ceiling.mgPerDay()) <= 0) {
                continue;
            }
            BigDecimal ratio = dose.divide(ceiling.mgPerDay(), 2, RoundingMode.DOWN);
            Map<String, String> evidence = new LinkedHashMap<>();
            evidence.put("每日合计", plain(dose) + " mg");
            evidence.put("适用上限", plain(ceiling.mgPerDay()) + " mg");
            evidence.put("人群", ceiling.population());
            evidence.put("超出倍数", ratio + "×");
            hits.add(new RiskHit(RiskRuleKind.DOSE_LIMIT, ceiling.row().getId(),
                    "LIMIT_" + component + "_" + ceiling.population(), null,
                    ratio.compareTo(SEVERE_RATIO) >= 0 ? Severity.A : Severity.B, List.of(component),
                    "重复成分叠加（含复方制剂中的同一成分）",
                    "每日合计 " + plain(dose) + " mg 已超过 " + ceiling.population() + " 上限 "
                            + plain(ceiling.mgPerDay()) + " mg，请核对是否同时在服多种含该成分的药",
                    plain(dose) + " mg/d > 上限 " + plain(ceiling.mgPerDay()) + " mg/d（人群 " + ceiling.population() + "）",
                    evidence, ceiling.row().getSourceRef(), ceiling.row().getSourceDate()));
        }
        return hits;
    }

    private record Limit(String population, BigDecimal mgPerDay, ComponentLimitEntity row) {
    }

    private Limit strictestCeiling(String component, List<ComponentLimitEntity> rows, RiskContext ctx,
                                   List<String> incomplete, Set<String> noted) {
        Limit strictest = null;
        for (ComponentLimitEntity row : rows) {
            if (!applies(row, ctx)) {
                continue;
            }
            BigDecimal ceiling = ceilingOf(row, ctx);
            if (ceiling == null) {
                if (noted.add(component + ":" + row.getPopulation())) {
                    incomplete.add("成分 " + component + " 的 " + row.getPopulation()
                            + " 上限算不出来（按公斤计但档案缺体重，或未填上限值），本次未做超量校验");
                }
                continue;
            }
            if (strictest == null || ceiling.compareTo(strictest.mgPerDay()) < 0) {
                strictest = new Limit(row.getPopulation(), ceiling, row);
            }
        }
        return strictest;
    }

    private static boolean applies(ComponentLimitEntity row, RiskContext ctx) {
        String population = row.getPopulation();
        if ("ADULT".equalsIgnoreCase(population)) {
            return true;
        }
        if (!TAG_POPULATIONS.contains(population)) {
            return false;
        }
        if (!ctx.tagCodes().contains(population)) {
            return false;
        }
        return row.getAgeMaxMonths() == null || ctx.ageYears() == null
                || ctx.ageYears() * 12 <= row.getAgeMaxMonths();
    }

    private static BigDecimal ceilingOf(ComponentLimitEntity row, RiskContext ctx) {
        BigDecimal factor = row.getReduceFactor() == null ? BigDecimal.ONE : row.getReduceFactor();
        BigDecimal ceiling = row.getMaxDailyMg();
        if (row.getMaxDailyMgPerKg() != null) {
            if (ctx.weightKg() == null) {
                return null;
            }
            BigDecimal byWeight = row.getMaxDailyMgPerKg().multiply(ctx.weightKg());
            ceiling = ceiling == null || ceiling.compareTo(byWeight) > 0 ? byWeight : ceiling;
        }
        return ceiling == null ? null : ceiling.multiply(factor);
    }

    private List<RiskHit> spacingHits(RiskContext ctx, RuleSet rules) {
        List<RiskHit> hits = new ArrayList<>();
        for (SpacingRuleEntity rule : rules.spacings()) {
            if (ctx.doseOf(rule.getComponentA()).signum() <= 0 || ctx.doseOf(rule.getComponentB()).signum() <= 0) {
                continue;
            }
            Map<String, String> evidence = new LinkedHashMap<>();
            evidence.put("最小间隔分钟", String.valueOf(rule.getMinGapMinutes()));
            hits.add(new RiskHit(RiskRuleKind.SPACING, rule.getId(), rule.getRuleCode(), rule.getVersion(),
                    Severity.C, List.of(rule.getComponentA(), rule.getComponentB()), rule.getReason(),
                    rule.getComponentA() + " 与 " + rule.getComponentB() + " 至少间隔 "
                            + rule.getMinGapMinutes() + " 分钟，排程会自动错开",
                    "两味成分同时在服，命中间隔规则",
                    evidence, rule.getSourceRef(), rule.getSourceDate()));
        }
        return hits;
    }

    private boolean gate(String expr, String ruleCode, RiskContext ctx, BigDecimal doseA, BigDecimal doseB,
                         List<String> failedRules) {
        if (!AviatorExpressionEvaluator.hasExpression(expr)) {
            return true;
        }
        Map<String, Object> env = ctx.expressionEnv();
        env.put("doseA", doseA.doubleValue());
        env.put("doseB", doseB.doubleValue());
        Optional<Boolean> result = expressions.test(expr, env);
        if (result.isEmpty()) {
            failedRules.add(ruleCode);
            return false;
        }
        return result.get();
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}

package com.homemedguard.service.rule.core;

import com.homemedguard.service.rule.entity.ComponentLimitEntity;
import com.homemedguard.service.rule.entity.ContraindicationRuleEntity;
import com.homemedguard.service.rule.entity.InteractionRuleEntity;
import com.homemedguard.service.rule.entity.SpacingRuleEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 规则引擎表驱动测试（handoff §一 硬性要求）。
 * 每条用例自带上下文与规则集，不连数据库——改判定口径必须动这张表。
 * 测试里的 sourceRef 一律写"测试用例（非真实说明书）"，避免把假规则混进真实库。
 */
@DisplayName("② 规则引擎：表驱动")
class RuleEvaluatorTest {

    private static final String APAP = "ACETAMINOPHEN";
    private static final String WARFARIN = "WARFARIN";
    private static final String IBUPROFEN = "IBUPROFEN";
    private static final String METRONIDAZOLE = "METRONIDAZOLE";

    private final RuleEvaluator evaluator = new RuleEvaluator(new AviatorExpressionEvaluator());

    private record Case(String name, RiskContext context, RuleSet rules, Consumer<EvaluationOutcome> expect) {
    }

    static Stream<Case> cases() {
        return Stream.of(
                new Case("两种成分同时在服即命中，A 级要阻断",
                        context(40, 70D, Set.of(), Map.of(APAP, "500", WARFARIN, "5")),
                        ruleSet(List.of(drugRule("INT-001", APAP, WARFARIN, "A", null)), List.of(), List.of(), Map.of()),
                        out -> {
                            assertThat(out.hits()).singleElement().satisfies(hit -> {
                                assertThat(hit.kind()).isEqualTo(RiskRuleKind.INTERACTION);
                                assertThat(hit.severity()).isEqualTo(Severity.A);
                                assertThat(hit.ruleCode()).isEqualTo("INT-001");
                                assertThat(hit.components()).containsExactly(APAP, WARFARIN);
                                assertThat(hit.basis()).contains("同时在服");
                            });
                            assertThat(out.blocking()).isTrue();
                        }),

                new Case("只有一味在服不命中",
                        context(40, 70D, Set.of(), Map.of(APAP, "500")),
                        ruleSet(List.of(drugRule("INT-001", APAP, WARFARIN, "A", null)), List.of(), List.of(), Map.of()),
                        out -> assertThat(out.hits()).isEmpty()),

                new Case("表达式附加条件成立才命中（儿童）",
                        context(3, 14D, Set.of("CHILD"), Map.of(IBUPROFEN, "100", WARFARIN, "5")),
                        ruleSet(List.of(drugRule("INT-003", IBUPROFEN, WARFARIN, "A", "ageYears < 12")),
                                List.of(), List.of(), Map.of()),
                        out -> assertThat(out.hits()).singleElement()
                                .satisfies(hit -> assertThat(hit.basis()).contains("附加条件成立"))),

                new Case("同一规则对成人不成立",
                        context(40, 70D, Set.of(), Map.of(IBUPROFEN, "100", WARFARIN, "5")),
                        ruleSet(List.of(drugRule("INT-003", IBUPROFEN, WARFARIN, "A", "ageYears < 12")),
                                List.of(), List.of(), Map.of()),
                        out -> assertThat(out.hits()).isEmpty()),

                new Case("剂量变量可以进表达式：超过阈值才报",
                        context(40, 70D, Set.of(), Map.of(APAP, "500", WARFARIN, "5")),
                        ruleSet(List.of(drugRule("INT-005", APAP, WARFARIN, "A", "doseA > 2000")),
                                List.of(), List.of(), Map.of()),
                        out -> assertThat(out.hits()).isEmpty()),

                new Case("表达式写错：记入 failedRuleCodes，绝不静默当成没风险",
                        context(40, 70D, Set.of(), Map.of(APAP, "500", WARFARIN, "5")),
                        ruleSet(List.of(drugRule("INT-004", APAP, WARFARIN, "A", "1 +")),
                                List.of(), List.of(), Map.of()),
                        out -> {
                            assertThat(out.hits()).isEmpty();
                            assertThat(out.failedRuleCodes()).containsExactly("INT-004");
                        }),

                new Case("药-酒：酒精以 target 形式命中，不要求其有剂量",
                        context(30, 60D, Set.of(), Map.of(METRONIDAZOLE, "400"), Set.of("ALCOHOL")),
                        ruleSet(List.of(alcoholRule("INT-002", METRONIDAZOLE, "ALCOHOL", "B")),
                                List.of(), List.of(), Map.of()),
                        out -> assertThat(out.hits()).singleElement()
                                .satisfies(hit -> assertThat(hit.basis()).contains("ALCOHOL"))),

                new Case("禁忌人群标签命中",
                        context(28, 55D, Set.of("PREGNANT"), Map.of(IBUPROFEN, "400")),
                        ruleSet(List.of(), List.of(contraRule("CON-001", IBUPROFEN, "PREGNANT", "A")),
                                List.of(), Map.of()),
                        out -> {
                            assertThat(out.hits()).singleElement().satisfies(hit -> {
                                assertThat(hit.kind()).isEqualTo(RiskRuleKind.CONTRAINDICATION);
                                assertThat(hit.severity().blocking()).isTrue();
                                assertThat(hit.evidence()).containsEntry("人群标签", "PREGNANT");
                            });
                            assertThat(out.blocking()).isTrue();
                        }),

                new Case("过敏史不依赖规则库覆盖度：空规则集也要命中",
                        context(35, 60D, Set.of(), Map.of(APAP, "1000"), Set.of(), Set.of(APAP)),
                        RuleSet.empty(),
                        out -> assertThat(out.hits()).singleElement().satisfies(hit -> {
                            assertThat(hit.ruleCode()).isEqualTo("MEMBER_ALLERGY_HISTORY");
                            assertThat(hit.severity()).isEqualTo(Severity.A);
                        })),

                new Case("复方叠加后超成人日上限，1.5 倍以上升级为 A",
                        context(40, 70D, Set.of(), Map.of(APAP, "3000")),
                        rulesWithLimits(APAP, List.of(adultLimit(APAP, "2000"))),
                        out -> assertThat(out.hits()).singleElement().satisfies(hit -> {
                            assertThat(hit.kind()).isEqualTo(RiskRuleKind.DOSE_LIMIT);
                            assertThat(hit.severity()).isEqualTo(Severity.A);
                            assertThat(hit.evidence()).containsEntry("超出倍数", "1.50×");
                        })),

                new Case("略超上限只给 B 级",
                        context(40, 70D, Set.of(), Map.of(APAP, "2200")),
                        rulesWithLimits(APAP, List.of(adultLimit(APAP, "2000"))),
                        out -> assertThat(out.hits()).singleElement()
                                .satisfies(hit -> assertThat(hit.severity()).isEqualTo(Severity.B))),

                new Case("未超上限不报",
                        context(40, 70D, Set.of(), Map.of(APAP, "1000")),
                        rulesWithLimits(APAP, List.of(adultLimit(APAP, "2000"))),
                        out -> assertThat(out.hits()).isEmpty()),

                new Case("儿童按 mg·kg⁻¹ 取更小的那个上限",
                        context(4, 14D, Set.of("CHILD"), Map.of(APAP, "500")),
                        rulesWithLimits(APAP, List.of(adultLimit(APAP, "2000"), childLimitByKg(APAP, "15", null))),
                        out -> assertThat(out.hits()).singleElement().satisfies(hit -> {
                            assertThat(hit.evidence()).containsEntry("适用上限", "210 mg");
                            assertThat(hit.evidence()).containsEntry("人群", "CHILD");
                        })),

                new Case("缺体重时不做公斤上限校验，但必须报判定不完整",
                        context(4, null, Set.of("CHILD"), Map.of(APAP, "500")),
                        rulesWithLimits(APAP, List.of(adultLimit(APAP, "2000"), childLimitByKg(APAP, "15", null))),
                        out -> {
                            assertThat(out.hits()).isEmpty();
                            assertThat(out.incompleteReasons()).anyMatch(reason -> reason.contains("缺体重"));
                        }),

                new Case("老年折减系数让上限变小",
                        context(80, 60D, Set.of("ELDERLY"), Map.of(APAP, "1500")),
                        rulesWithLimits(APAP, List.of(adultLimit(APAP, "2000"), elderlyLimit(APAP, "2000", "0.70"))),
                        out -> assertThat(out.hits()).singleElement().satisfies(hit -> {
                            assertThat(hit.evidence()).containsEntry("适用上限", "1400 mg");
                            assertThat(hit.evidence()).containsEntry("人群", "ELDERLY");
                        })),

                new Case("库里没有该成分上限：报不完整而不是判安全",
                        context(40, 70D, Set.of(), Map.of("UNKNOWN_X", "100")),
                        RuleSet.empty(),
                        out -> assertThat(out.incompleteReasons()).anyMatch(reason -> reason.contains("UNKNOWN_X"))),

                new Case("需间隔只给 C 级，并把分钟数交给 ③",
                        context(50, 65D, Set.of(), Map.of("CALCIUM_CARBONATE", "600", "LEVOFLOXACIN", "500")),
                        ruleSet(List.of(), List.of(), List.of(spacingRule("SPA-001", "CALCIUM_CARBONATE",
                                "LEVOFLOXACIN", 120)), Map.of()),
                        out -> assertThat(out.hits()).singleElement().satisfies(hit -> {
                            assertThat(hit.kind()).isEqualTo(RiskRuleKind.SPACING);
                            assertThat(hit.severity()).isEqualTo(Severity.C);
                            assertThat(hit.evidence()).containsEntry("最小间隔分钟", "120");
                        })));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void evaluateAgainstExpectedOutcome(Case testCase) {
        testCase.expect().accept(evaluator.evaluate(testCase.context(), testCase.rules()));
    }

    // ---- 构造器 ----------------------------------------------------------------

    private static RiskContext context(double ageYears, Double weightKg, Set<String> tags, Map<String, String> doses) {
        return context(ageYears, weightKg, tags, doses, Set.of(), Set.of());
    }

    private static RiskContext context(double ageYears, Double weightKg, Set<String> tags, Map<String, String> doses,
                                       Set<String> targets) {
        return context(ageYears, weightKg, tags, doses, targets, Set.of());
    }

    private static RiskContext context(double ageYears, Double weightKg, Set<String> tags, Map<String, String> doses,
                                       Set<String> targets, Set<String> allergies) {
        Map<String, BigDecimal> daily = new TreeMap<>();
        doses.forEach((code, mg) -> daily.put(code, new BigDecimal(mg)));
        return new RiskContext(1L, 10L, ageYears, weightKg == null ? null : BigDecimal.valueOf(weightKg),
                tags, allergies, null, null, daily, targets, List.of());
    }

    private static RuleSet ruleSet(List<InteractionRuleEntity> interactions,
                                   List<ContraindicationRuleEntity> contraindications,
                                   List<SpacingRuleEntity> spacings,
                                   Map<String, List<ComponentLimitEntity>> limits) {
        return new RuleSet(interactions, contraindications, spacings, limits, Map.of(), Map.of(), Map.of(), "test#n3");
    }

    private static RuleSet rulesWithLimits(String component, List<ComponentLimitEntity> limits) {
        return ruleSet(List.of(), List.of(), List.of(), Map.of(component, limits));
    }

    private static InteractionRuleEntity drugRule(String code, String a, String b, String severity, String expr) {
        InteractionRuleEntity rule = new InteractionRuleEntity();
        rule.setId(1L);
        rule.setRuleCode(code);
        rule.setVersion(1);
        rule.setStatus("PUBLISHED");
        rule.setTargetType("DRUG");
        rule.setComponentA(a);
        rule.setComponentB(b);
        rule.setSeverity(severity);
        rule.setMechanism("测试机制");
        rule.setAdvice("测试建议");
        rule.setExpr(expr);
        rule.setSourceRef("测试用例（非真实说明书）");
        rule.setSourceDate(LocalDate.of(2026, 11, 1));
        return rule;
    }

    private static InteractionRuleEntity alcoholRule(String code, String component, String target, String severity) {
        InteractionRuleEntity rule = drugRule(code, component, target, severity, null);
        rule.setTargetType("ALCOHOL");
        return rule;
    }

    private static ContraindicationRuleEntity contraRule(String code, String component, String tag, String severity) {
        ContraindicationRuleEntity rule = new ContraindicationRuleEntity();
        rule.setId(2L);
        rule.setRuleCode(code);
        rule.setVersion(1);
        rule.setStatus("PUBLISHED");
        rule.setComponent(component);
        rule.setTagCode(tag);
        rule.setSeverity(severity);
        rule.setAdvice("测试禁忌建议");
        rule.setSourceRef("测试用例（非真实说明书）");
        rule.setSourceDate(LocalDate.of(2026, 11, 1));
        return rule;
    }

    private static SpacingRuleEntity spacingRule(String code, String a, String b, int gapMinutes) {
        SpacingRuleEntity rule = new SpacingRuleEntity();
        rule.setId(3L);
        rule.setRuleCode(code);
        rule.setVersion(1);
        rule.setStatus("PUBLISHED");
        rule.setComponentA(a);
        rule.setComponentB(b);
        rule.setMinGapMinutes(gapMinutes);
        rule.setReason("测试间隔原因");
        rule.setSourceRef("测试用例（非真实说明书）");
        rule.setSourceDate(LocalDate.of(2026, 11, 1));
        return rule;
    }

    private static ComponentLimitEntity adultLimit(String component, String maxDailyMg) {
        return limit(component, "ADULT", maxDailyMg, null, "1.00");
    }

    private static ComponentLimitEntity childLimitByKg(String component, String mgPerKg, String maxDailyMg) {
        return limit(component, "CHILD", maxDailyMg, mgPerKg, "1.00");
    }

    private static ComponentLimitEntity elderlyLimit(String component, String maxDailyMg, String reduceFactor) {
        return limit(component, "ELDERLY", maxDailyMg, null, reduceFactor);
    }

    private static ComponentLimitEntity limit(String component, String population, String maxDailyMg,
                                              String mgPerKg, String reduceFactor) {
        ComponentLimitEntity row = new ComponentLimitEntity();
        row.setId(4L);
        row.setPopulation(population);
        row.setMaxDailyMg(maxDailyMg == null ? null : new BigDecimal(maxDailyMg));
        row.setMaxDailyMgPerKg(mgPerKg == null ? null : new BigDecimal(mgPerKg));
        row.setReduceFactor(new BigDecimal(reduceFactor));
        row.setSourceRef("测试用例（非真实说明书）");
        row.setSourceDate(LocalDate.of(2026, 11, 1));
        return row;
    }
}

package com.homemedguard.service.rule.core;

import com.homemedguard.common.error.BizException;
import com.homemedguard.common.contract.MemberContextDTO;
import com.homemedguard.service.rule.api.RiskEvaluateFacade.DoseInput;
import com.homemedguard.service.rule.api.RiskEvaluateFacade.Request;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 上下文装配：成分归一化、按剂型含量折回每日 mg、重复成分叠加、派生标签。
 * 这一步算错，后面所有判定都是错的，所以单独测。
 */
@DisplayName("② 上下文装配")
class ContextBuilderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    private static final String APAP = "ACETAMINOPHEN";
    private static final String CAFEINE = "CAFFEINE";

    private final ContextBuilder builder = new ContextBuilder();

    /** 药典里 1 片复方含 APAP 500mg + 咖啡因 30mg；另有一盒只登记了成分名。 */
    private static RuleSet rules() {
        return new RuleSet(
                List.of(), List.of(), List.of(), Map.of(),
                Map.of("acetaminophen", APAP, "对乙酰氨基酚", APAP, "caffeine", CAFEINE),
                Map.of(APAP, "mg", CAFEINE, "mg"),
                Map.of(100L, List.of(
                        new RuleSet.DrugComponentView(APAP, new BigDecimal("500"), "片", "CONFIRMED"),
                        new RuleSet.DrugComponentView(CAFEINE, new BigDecimal("30"), "片", "PENDING"))),
                "test#n0");
    }

    private static MemberContextDTO member(LocalDate birthDate, Double weightKg, Set<String> tags,
                                           String liverGrade, Integer renalEgfr) {
        return new MemberContextDTO(10L, 1L, "测试成员", birthDate,
                weightKg == null ? null : BigDecimal.valueOf(weightKg), tags, Set.of(), liverGrade, renalEgfr);
    }

    @Test
    @DisplayName("同一种成分来自两盒药也要先叠加，再交给上限校验")
    void accumulatesSameComponentAcrossDrugsAndFreeText() {
        Request request = new Request(10L, "REGIMEN_ADD", List.of(
                new DoseInput(100L, null, new BigDecimal("2"), null, 3),
                new DoseInput(null, "对乙酰氨基酚", new BigDecimal("500"), "mg", 2)),
                List.of());

        RiskContext context = builder.build(member(LocalDate.of(2022, 1, 1), 14D, Set.of(), null, null),
                request, rules(), TODAY);

        // 500*2*3 = 3000 来自复方，500*2 = 1000 来自单味填报
        assertThat(context.doseOf(APAP)).isEqualByComparingTo("4000");
        assertThat(context.doseOf(CAFEINE)).isEqualByComparingTo("180");
        assertThat(context.unresolvedComponents()).anyMatch(note -> note.contains(CAFEINE) && note.contains("待人工确认"));
    }

    @Test
    @DisplayName("归不到标准成分的词必须显式回传，不能当成没风险")
    void reportsUnnormalizableComponents() {
        Request request = new Request(10L, "REGIMEN_ADD",
                List.of(new DoseInput(null, "扑热息痛缓释胶囊", new BigDecimal("500"), "mg", 2)), List.of());

        RiskContext context = builder.build(member(LocalDate.of(1990, 1, 1), 70D, Set.of(), null, null),
                request, rules(), TODAY);

        assertThat(context.components()).isEmpty();
        assertThat(context.unresolvedComponents()).singleElement()
                .satisfies(note -> assertThat(note).contains("不在成分库内"));
    }

    @Test
    @DisplayName("没登记成分关系的药，报未归一化而不是静默跳过")
    void reportsDrugWithoutNormalization() {
        Request request = new Request(10L, "PRESCRIBE",
                List.of(new DoseInput(999L, null, new BigDecimal("1"), null, 1)), List.of());

        RiskContext context = builder.build(member(LocalDate.of(1990, 1, 1), 70D, Set.of(), null, null),
                request, rules(), TODAY);

        assertThat(context.unresolvedComponents()).anyMatch(note -> note.contains("drug#999"));
    }

    @Test
    @DisplayName("儿童/老年/肝肾标签由数值派生，① 不需要存这些结论")
    void derivesPopulationTagsFromNumbers() {
        RiskContext child = builder.build(member(LocalDate.of(2022, 6, 1), 14D, Set.of(), null, null),
                emptyRequest(), rules(), TODAY);
        RiskContext elderly = builder.build(member(LocalDate.of(1955, 6, 1), 60D, Set.of(), "B", 45),
                emptyRequest(), rules(), TODAY);

        assertThat(child.tagCodes()).contains("CHILD");
        assertThat(elderly.tagCodes()).contains("ELDERLY", "HEPATIC_IMPAIRED", "RENAL_IMPAIRED");
        assertThat(elderly.ageYears()).isEqualTo(71D);
    }

    @Test
    @DisplayName("IU 没有换算系数时直接报错，禁止猜")
    void rejectsIuWithoutConversionFactor() {
        Request request = new Request(10L, "REGIMEN_ADD",
                List.of(new DoseInput(null, APAP, new BigDecimal("1000"), "IU", 1)), List.of());

        assertThatThrownBy(() -> builder.build(member(LocalDate.of(1990, 1, 1), 70D, Set.of(), null, null),
                request, rules(), TODAY))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("IU");
    }

    private static Request emptyRequest() {
        return new Request(10L, "FAMILY_SCAN", List.of(new DoseInput(null, APAP, new BigDecimal("500"), "mg", 1)),
                List.of());
    }
}

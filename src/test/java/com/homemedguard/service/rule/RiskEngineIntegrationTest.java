package com.homemedguard.service.rule;

import com.homemedguard.common.contract.MemberContextDTO;
import com.homemedguard.common.contract.MemberProfileQueryFacade;
import com.homemedguard.service.rule.api.RiskEvaluateFacade;
import com.homemedguard.service.rule.api.SpacingRuleQueryFacade;
import com.homemedguard.service.rule.core.RuleSetProvider;
import com.homemedguard.service.rule.entity.ComponentAliasEntity;
import com.homemedguard.service.rule.entity.ComponentDictEntity;
import com.homemedguard.service.rule.entity.ComponentLimitEntity;
import com.homemedguard.service.rule.entity.ContraindicationRuleEntity;
import com.homemedguard.service.rule.entity.InteractionRuleEntity;
import com.homemedguard.service.rule.entity.RiskEvaluationLogEntity;
import com.homemedguard.service.rule.entity.SpacingRuleEntity;
import com.homemedguard.service.rule.mapper.ComponentAliasMapper;
import com.homemedguard.service.rule.mapper.ComponentDictMapper;
import com.homemedguard.service.rule.mapper.ComponentLimitMapper;
import com.homemedguard.service.rule.mapper.ContraindicationRuleMapper;
import com.homemedguard.service.rule.mapper.InteractionRuleMapper;
import com.homemedguard.service.rule.mapper.RiskEvaluationLogMapper;
import com.homemedguard.service.rule.mapper.SpacingRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ② 端到端：Flyway 建表 → 规则入库 → /api/risk/evaluate 出结论 → 留痕落库。
 * 成员档案用测试替身实现 ① 的契约，证明 ② 可以脱离 ① 独立跑通（模块化铁律）。
 */
@SpringBootTest(properties = "app.module.rule=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "tester", roles = "MEMBER")
@DisplayName("② 规则引擎端到端（H2 + 契约替身）")
class RiskEngineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RuleSetProvider ruleSetProvider;

    @Autowired
    private SpacingRuleQueryFacade spacingRuleQueryFacade;

    @Autowired
    private ComponentDictMapper componentDictMapper;

    @Autowired
    private ComponentAliasMapper componentAliasMapper;

    @Autowired
    private ComponentLimitMapper componentLimitMapper;

    @Autowired
    private InteractionRuleMapper interactionRuleMapper;

    @Autowired
    private ContraindicationRuleMapper contraindicationRuleMapper;

    @Autowired
    private SpacingRuleMapper spacingRuleMapper;

    @Autowired
    private RiskEvaluationLogMapper riskEvaluationLogMapper;

    @TestConfiguration
    static class MemberProfileStubConfig {

        @Bean
        MemberProfileQueryFacade stubMemberProfiles() {
            return new MemberProfileQueryFacade() {
                @Override
                public Optional<MemberContextDTO> memberContext(Long memberId) {
                    if (Long.valueOf(10L).equals(memberId)) {
                        return Optional.of(new MemberContextDTO(10L, 1L, "父亲", LocalDate.of(1985, 5, 1),
                                new BigDecimal("70"), Set.of(), List.of(), "A", null));
                    }
                    if (Long.valueOf(11L).equals(memberId)) {
                        return Optional.of(new MemberContextDTO(11L, 1L, "妈妈", LocalDate.of(1992, 3, 1),
                                new BigDecimal("55"), Set.of("PREGNANT"), List.of("布洛芬"), null, null));
                    }
                    return Optional.empty();
                }

                @Override
                public List<MemberContextDTO> familyMemberContexts(Long familyId) {
                    return List.of();
                }
            };
        }
    }

    @BeforeEach
    void seedDictionary() {
        long apap = component(1, "ACETAMINOPHEN", "对乙酰氨基酚");
        long warfarin = component(2, "WARFARIN", "华法林");
        long ibuprofen = component(3, "IBUPROFEN", "布洛芬");
        alias("扑热息痛", apap);
        alias("acetaminophen", apap);
        alias(warfarinName(), warfarin);
        limit(apap, "ADULT", "2000", null, "1.00");
        limit(ibuprofen, "PREGNANT", "1200", null, "1.00");
        interaction("INT-DB-001", "ACETAMINOPHEN", "WARFARIN", "A", "PUBLISHED");
        interaction("INT-DB-DRAFT", "IBUPROFEN", "WARFARIN", "A", "DRAFT");
        contraindication("CON-DB-001", "IBUPROFEN", "PREGNANT", "A");
        spacing("SPA-DB-001", "ACETAMINOPHEN", "WARFARIN", 60);
        ruleSetProvider.invalidate();
    }

    private static String warfarinName() {
        return "华法林";
    }

    @Test
    @DisplayName("命中已发布规则、返回依据与版本、并写一条评估留痕")
    void evaluateHitsPublishedRuleAndWritesTrace() throws Exception {
        String body = mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":10,"scenario":"REGIMEN_ADD","items":[
                                  {"componentRef":"扑热息痛","doseAmount":500,"doseUnit":"mg","timesPerDay":3},
                                  {"componentRef":"华法林","doseAmount":5,"doseUnit":"mg","timesPerDay":1}
                                ]}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(body).contains("\"blocking\":true")
                .contains("INT-DB-001")
                .contains("测试来源（非真实说明书）")
                .contains("家庭用药提醒辅助工具");

        List<RiskEvaluationLogEntity> logs = riskEvaluationLogMapper.selectList(null);
        assertThat(logs).singleElement().satisfies(row -> {
            assertThat(row.getHitCount()).isPositive();
            assertThat(row.getMaxSeverity()).isEqualTo("A");
            assertThat(row.getRuleSetStamp()).isNotBlank();
            assertThat(row.getInputSnapshot()).contains("ACETAMINOPHEN");
        });
    }

    @Test
    @DisplayName("DRAFT 规则不参与判定，超量与禁忌各出一条")
    void ignoresDraftRulesAndReportsDoseCeiling() throws Exception {
        String body = mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":10,"scenario":"PRESCRIBE","items":[
                                  {"componentRef":"acetaminophen","doseAmount":2,"doseUnit":"g","timesPerDay":2}
                                ]}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(body).contains("LIMIT_ACETAMINOPHEN_ADULT")
                .doesNotContain("INT-DB-DRAFT");
    }

    @Test
    @DisplayName("孕妇档案命中禁忌人群，同时命中过敏史（布洛芬在过敏史里）")
    void hitsContraindicationAndAllergyHistory() throws Exception {
        String body = mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":11,"scenario":"REGIMEN_ADD","items":[
                                  {"componentRef":"布洛芬","doseAmount":400,"doseUnit":"mg","timesPerDay":3}
                                ]}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(body).contains("CON-DB-001").contains("MEMBER_ALLERGY_HISTORY");
    }

    @Test
    @DisplayName("酒精目标只在传了 targets 时命中")
    void hitsAlcoholTargetOnlyWhenDeclared() throws Exception {
        interactionRuleMapper.insert(alcoholInteraction());
        ruleSetProvider.invalidate();

        String withoutAlcohol = mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":10,"scenario":"REGIMEN_ADD","items":[
                                  {"componentRef":"ACETAMINOPHEN","doseAmount":500,"doseUnit":"mg","timesPerDay":3}]}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        String withAlcohol = mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":10,"scenario":"REGIMEN_ADD","targets":["ALCOHOL"],"items":[
                                  {"componentRef":"ACETAMINOPHEN","doseAmount":500,"doseUnit":"mg","timesPerDay":3}]}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(withoutAlcohol).doesNotContain("INT-DB-ALCOHOL");
        assertThat(withAlcohol).contains("INT-DB-ALCOHOL");
    }

    @Test
    @DisplayName("成员不存在返回 404 业务码，不给结论")
    void unknownMemberIsRejected() throws Exception {
        mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":999,"scenario":"REGIMEN_ADD","items":[
                                  {"componentRef":"ACETAMINOPHEN","doseAmount":500,"doseUnit":"mg","timesPerDay":1}]}"""))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("③ 拿间隔时不分方向，多条取最严")
    void spacingQueryIsOrderAgnostic() {
        assertThat(spacingRuleQueryFacade.minGapBetween("WARFARIN", "ACETAMINOPHEN"))
                .get()
                .satisfies(requirement -> assertThat(requirement.minGapMinutes()).isEqualTo(60));
        assertThat(spacingRuleQueryFacade.gapsWithin(List.of("ACETAMINOPHEN", "WARFARIN", "IBUPROFEN")))
                .singleElement();
        assertThat(spacingRuleQueryFacade.minGapBetween("WARFARIN", "IBUPROFEN")).isEmpty();
    }

    // ---- 种子数据（全部标注为测试来源，绝不冒充说明书）--------------------------------

    private long component(int order, String code, String standardName) {
        ComponentDictEntity entity = new ComponentDictEntity();
        entity.setComponentCode(code);
        entity.setStandardName(standardName);
        entity.setDoseUnit("mg");
        entity.setTherapeuticGroup("测试分组" + order);
        componentDictMapper.insert(entity);
        return entity.getId();
    }

    private void alias(String alias, long componentId) {
        ComponentAliasEntity entity = new ComponentAliasEntity();
        entity.setAlias(alias);
        entity.setComponentId(componentId);
        componentAliasMapper.insert(entity);
    }

    private void limit(long componentId, String population, String maxDailyMg, String mgPerKg, String reduceFactor) {
        ComponentLimitEntity entity = new ComponentLimitEntity();
        entity.setComponentId(componentId);
        entity.setPopulation(population);
        entity.setMaxDailyMg(maxDailyMg == null ? null : new BigDecimal(maxDailyMg));
        entity.setMaxDailyMgPerKg(mgPerKg == null ? null : new BigDecimal(mgPerKg));
        entity.setReduceFactor(new BigDecimal(reduceFactor));
        entity.setSourceRef("测试来源（非真实说明书）");
        entity.setSourceDate(LocalDate.of(2026, 11, 1));
        componentLimitMapper.insert(entity);
    }

    private void interaction(String ruleCode, String a, String b, String severity, String status) {
        InteractionRuleEntity entity = new InteractionRuleEntity();
        entity.setRuleCode(ruleCode);
        entity.setVersion(1);
        entity.setStatus(status);
        entity.setTargetType("DRUG");
        entity.setComponentA(a);
        entity.setComponentB(b);
        entity.setSeverity(severity);
        entity.setMechanism("测试机制");
        entity.setAdvice("测试建议：请遵医嘱");
        entity.setSourceRef("测试来源（非真实说明书）");
        entity.setSourceDate(LocalDate.of(2026, 11, 1));
        interactionRuleMapper.insert(entity);
    }

    private InteractionRuleEntity alcoholInteraction() {
        InteractionRuleEntity entity = new InteractionRuleEntity();
        entity.setRuleCode("INT-DB-ALCOHOL");
        entity.setVersion(1);
        entity.setStatus("PUBLISHED");
        entity.setTargetType("ALCOHOL");
        entity.setComponentA("ACETAMINOPHEN");
        entity.setComponentB("ALCOHOL");
        entity.setSeverity("B");
        entity.setAdvice("测试建议：饮酒期间避免服用");
        entity.setSourceRef("测试来源（非真实说明书）");
        entity.setSourceDate(LocalDate.of(2026, 11, 1));
        return entity;
    }

    private void contraindication(String ruleCode, String component, String tag, String severity) {
        ContraindicationRuleEntity entity = new ContraindicationRuleEntity();
        entity.setRuleCode(ruleCode);
        entity.setVersion(1);
        entity.setStatus("PUBLISHED");
        entity.setComponent(component);
        entity.setTagCode(tag);
        entity.setSeverity(severity);
        entity.setAdvice("测试禁忌建议");
        entity.setSourceRef("测试来源（非真实说明书）");
        entity.setSourceDate(LocalDate.of(2026, 11, 1));
        contraindicationRuleMapper.insert(entity);
    }

    private void spacing(String ruleCode, String a, String b, int gapMinutes) {
        SpacingRuleEntity entity = new SpacingRuleEntity();
        entity.setRuleCode(ruleCode);
        entity.setVersion(1);
        entity.setStatus("PUBLISHED");
        entity.setComponentA(a);
        entity.setComponentB(b);
        entity.setMinGapMinutes(gapMinutes);
        entity.setReason("测试间隔原因");
        entity.setSourceRef("测试来源（非真实说明书）");
        entity.setSourceDate(LocalDate.of(2026, 11, 1));
        spacingRuleMapper.insert(entity);
    }
}

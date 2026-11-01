package com.homemedguard.service.rule.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homemedguard.service.rule.core.RuleSetProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 规则审核发布流：草稿 → 提交 → 发布 → 新版本取代 → 停用，并且每一步都留下流转记录。
 * 同时验证"没发布的规则不参与判定"和"只有系统管理员能动规则"。
 */
@SpringBootTest(properties = {
        "app.module.family=true", "app.module.drug=true", "app.module.rule=true",
        "app.auth.bootstrap.username=rule_admin", "app.auth.bootstrap.password=Passw0rd!"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("M3 规则审核发布流")
class RuleGovernanceFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RuleSetProvider ruleSetProvider;

    @Test
    @DisplayName("草稿不判定，发布后才命中，流转记录一步不落")
    void draftIsInvisibleUntilPublished() throws Exception {
        String admin = login("rule_admin", "Passw0rd!");
        seedAcetaminophenAndWarfarin();

        String draft = draftJson("INT-GOV-001", null);
        JsonNode created = postJson(admin, "/api/admin/rules/interaction", draft);
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(publishedCount("INT-GOV-001")).isZero();

        postJson(admin, "/api/admin/rules/interaction/INT-GOV-001/1/submit", "{}");
        assertThat(publishedCount("INT-GOV-001")).isZero();

        JsonNode approved = postJson(admin,
                "/api/admin/rules/interaction/INT-GOV-001/1/approve", "{\"effectiveFrom\":\"2026-01-01\"}");
        assertThat(approved.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(approved.path("effectiveFrom").asText()).isEqualTo("2026-01-01");
        assertThat(publishedCount("INT-GOV-001")).isEqualTo(1);

        List<String> actions = jdbcTemplate.queryForList(
                "SELECT action FROM rule_publish_log WHERE rule_code = 'INT-GOV-001' ORDER BY id", String.class);
        assertThat(actions).containsExactly("CREATE", "SUBMIT", "APPROVE");

        JsonNode report = evaluate(admin);
        assertThat(report.path("items").toString()).contains("INT-GOV-001");
        assertThat(report.path("blocking").asBoolean()).isTrue();
        assertThat(report.path("ruleSetStamp").asText()).isNotBlank();
    }

    @Test
    @DisplayName("发布新版本会自动停用旧版本，不会两条同时命中")
    void publishingNewVersionSupersedesOld() throws Exception {
        String admin = login("rule_admin", "Passw0rd!");
        seedAcetaminophenAndWarfarin();

        publish(admin, draftJson("INT-GOV-002", null));
        publish(admin, draftJson("INT-GOV-002", 2));

        assertThat(publishedCount("INT-GOV-002")).isEqualTo(1);
        Integer retired = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interaction_rule WHERE rule_code = 'INT-GOV-002' AND status = 'RETIRED'",
                Integer.class);
        assertThat(retired).isEqualTo(1);

        JsonNode report = evaluate(admin);
        assertThat(report.path("items").toString()).contains("\"ruleVersion\":2")
                .doesNotContain("\"ruleVersion\":1");
    }

    @Test
    @DisplayName("没来源连草稿都存不进去；没提交不能发布；已发布不能原地改")
    void sourceIsMandatoryAndPublishedRowsAreImmutable() throws Exception {
        String admin = login("rule_admin", "Passw0rd!");
        seedAcetaminophenAndWarfarin();

        mockMvc.perform(post("/api/admin/rules/interaction")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftWithoutSource("INT-GOV-003")))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("规则必须带来源 sourceRef"));

        postJson(admin, "/api/admin/rules/interaction", draftJson("INT-GOV-005", null));
        mockMvc.perform(post("/api/admin/rules/interaction/INT-GOV-005/1/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("只有待审核的规则可以发布"));

        publish(admin, draftJson("INT-GOV-004", null));
        mockMvc.perform(post("/api/admin/rules/interaction")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftJson("INT-GOV-004", null)))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("不可原地改"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("表达式写错、驳回不写理由、停用已停用的都会被挡")
    void invalidTransitionsAndExpressionsAreRejected() throws Exception {
        String admin = login("rule_admin", "Passw0rd!");
        seedAcetaminophenAndWarfarin();

        mockMvc.perform(post("/api/admin/rules/interaction")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftJson("INT-GOV-005", null).replace("\"advice\":\"测试建议：请遵医嘱\"",
                                "\"advice\":\"测试建议：请遵医嘱\",\"expr\":\"1 +\"")))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("表达式编译不通过"));

        postJson(admin, "/api/admin/rules/interaction", draftJson("INT-GOV-006", null));
        mockMvc.perform(post("/api/admin/rules/interaction/INT-GOV-006/1/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        publish(admin, draftJson("INT-GOV-007", null));
        postJson(admin, "/api/admin/rules/interaction/INT-GOV-007/1/retire", "{\"reason\":\"测试停用\"}");
        mockMvc.perform(post("/api/admin/rules/interaction/INT-GOV-007/1/retire")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"重复停用\"}"))
                .andExpect(status().isBadRequest());

        assertThat(evaluate(admin).path("items").toString()).doesNotContain("INT-GOV-007");
    }

    @Test
    @DisplayName("普通成员进不了后台规则接口")
    void ordinaryMemberCannotReachAdminApi() throws Exception {
        register("plain_user", "Passw0rd!");
        String memberToken = login("plain_user", "Passw0rd!");

        mockMvc.perform(get("/api/admin/rules/interaction").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("\"code\":40300"));
    }

    @Test
    @DisplayName("全程只用后台接口：建成分→加别名→发布规则→评估命中，不靠手工塞数据库")
    void adminApiAloneDrivesTheFullLoop() throws Exception {
        String admin = login("rule_admin", "Passw0rd!");

        long acetaminophen = postJson(admin, "/api/admin/rules/components", """
                {"componentCode":"paracetamol_component","standardName":"对乙酰氨基酚","doseUnit":"mg",
                 "therapeuticGroup":"ANALGESIC"}""").path("id").asLong();
        postJson(admin, "/api/admin/rules/components/" + acetaminophen + "/aliases",
                "{\"alias\":\"扑热息痛\"}");
        postJson(admin, "/api/admin/rules/components/" + acetaminophen + "/aliases",
                "{\"alias\":\"APAP\"}");

        long warfarin = postJson(admin, "/api/admin/rules/components", """
                {"componentCode":"WARFARIN","standardName":"华法林","doseUnit":"mg",
                 "therapeuticGroup":"ANTICOAGULANT"}""").path("id").asLong();
        assertThat(warfarin).isPositive();

        postJson(admin, "/api/admin/rules/interaction", """
                {"ruleCode":"INT-FROM-API","componentA":"PARACETAMOL_COMPONENT","componentB":"WARFARIN",
                 "severity":"A","mechanism":"测试机制","advice":"测试建议：请遵医嘱",
                 "sourceRef":"测试来源（非真实说明书）","sourceDate":"2026-11-01"}""");
        postJson(admin, "/api/admin/rules/interaction/INT-FROM-API/1/submit", "{}");
        postJson(admin, "/api/admin/rules/interaction/INT-FROM-API/1/approve", "{}");

        String memberToken = registerAndLoginMember("member_api");
        long memberId = getJson(memberToken, "/api/family").path("members").get(0).path("memberId").asLong();
        JsonNode report = postJson(memberToken, "/api/risk/evaluate", """
                {"memberId":%d,"scenario":"PRESCRIBE","items":[
                  {"componentRef":"扑热息痛","doseAmount":500,"doseUnit":"mg","timesPerDay":3},
                  {"componentRef":"华法林","doseAmount":5,"doseUnit":"mg","timesPerDay":1}]}"""
                .formatted(memberId));

        assertThat(report.path("items").toString()).contains("INT-FROM-API");
        assertThat(report.path("blocking").asBoolean()).isTrue();
        assertThat(report.path("incompleteReasons").toString()).doesNotContain("不在成分库内");
    }

    // ---- helper ------------------------------------------------------------

    private void seedAcetaminophenAndWarfarin() {
        jdbcTemplate.update("""
                INSERT INTO component_dict (component_code, standard_name, dose_unit, therapeutic_group) VALUES
                ('ACETAMINOPHEN','对乙酰氨基酚','mg','ANALGESIC'),
                ('WARFARIN','华法林','mg','ANTICOAGULANT')""");
        ruleSetProvider.invalidate();
    }

    private String draftJson(String ruleCode, Integer version) {
        return "{" + "\"ruleCode\":\"" + ruleCode + "\""
                + (version == null ? "" : ",\"version\":" + version)
                + ",\"componentA\":\"ACETAMINOPHEN\",\"componentB\":\"WARFARIN\",\"severity\":\"A\","
                + "\"mechanism\":\"测试机制\",\"advice\":\"测试建议：请遵医嘱\","
                + "\"sourceRef\":\"测试来源（非真实说明书）\",\"sourceDate\":\"2026-11-01\"}";
    }

    private String draftWithoutSource(String ruleCode) {
        return "{" + "\"ruleCode\":\"" + ruleCode + "\","
                + "\"componentA\":\"ACETAMINOPHEN\",\"componentB\":\"WARFARIN\",\"severity\":\"A\","
                + "\"advice\":\"测试建议：请遵医嘱\"}";
    }

    private int publishedCount(String ruleCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interaction_rule WHERE rule_code = ? AND status = 'PUBLISHED'",
                Integer.class, ruleCode);
    }

    private void publish(String admin, String draft) throws Exception {
        String ruleCode = field(draft, "ruleCode");
        String version = field(draft, "version");
        postJson(admin, "/api/admin/rules/interaction", draft);
        postJson(admin, "/api/admin/rules/interaction/" + ruleCode + "/" + version + "/submit", "{}");
        postJson(admin, "/api/admin/rules/interaction/" + ruleCode + "/" + version + "/approve", "{}");
    }

    private static String field(String json, String name) {
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            String value = node.path(name).asText();
            return value.isEmpty() ? "1" : value;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private JsonNode evaluate(String adminToken) throws Exception {
        long memberId = getJson(adminToken, "/api/family").path("members").get(0).path("memberId").asLong();
        ruleSetProvider.invalidate();
        return postJson(adminToken, "/api/risk/evaluate", """
                {"memberId":%d,"scenario":"PRESCRIBE","items":[
                  {"componentRef":"对乙酰氨基酚","doseAmount":500,"doseUnit":"mg","timesPerDay":3},
                  {"componentRef":"华法林","doseAmount":5,"doseUnit":"mg","timesPerDay":1}]}""".formatted(memberId));
    }

    private String registerAndLoginMember(String username) throws Exception {
        register(username, "Passw0rd!");
        return login(username, "Passw0rd!");
    }

    private void register(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private JsonNode getJson(String token, String path) throws Exception {
        String body = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    private JsonNode postJson(String token, String path, String payload) throws Exception {
        String body = mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}

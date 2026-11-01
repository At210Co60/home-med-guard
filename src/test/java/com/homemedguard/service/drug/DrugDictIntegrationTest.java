package com.homemedguard.service.drug;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 药品字典 + ①→② 的成分归一化闭环。
 * 字典只放通用名/剂型/规格这类事实字段，批准文号一律留空。
 */
@SpringBootTest(properties = {"app.module.family=true", "app.module.drug=true", "app.module.rule=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("M1 药品字典与归一化交接")
class DrugDictIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RuleSetProvider ruleSetProvider;

    @Test
    @DisplayName("V5 演示字典可被关键词搜索命中，最多返回 10 条")
    void suggestHitsSeededDictionary() throws Exception {
        String token = registerAndLogin("father10");
        JsonNode hits = suggest(token, "对乙酰");

        assertThat(hits.toString()).contains("对乙酰氨基酚片");
        assertThat(hits.size()).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("用户自建药只有自己能搜到，系统字典大家都能搜到")
    void selfCreatedDrugStaysPrivate() throws Exception {
        String owner = registerAndLogin("owner11");
        String other = registerAndLogin("other11");

        postJson(owner, "/api/drugs", """
                {"genericName":"家用退热贴demo","dosageForm":"贴剂","specification":"10贴","otcRx":"OTC"}""");

        assertThat(suggest(owner, "家用退热贴demo").toString()).contains("家用退热贴demo");
        assertThat(suggest(other, "家用退热贴demo").toString()).doesNotContain("家用退热贴demo");
        assertThat(suggest(other, "布洛芬").toString()).contains("布洛芬片");
    }

    @Test
    @DisplayName("条码查不到返回 404 + 明确提示走手工录入")
    void unknownBarcodeFallsBackToManual() throws Exception {
        String token = registerAndLogin("father12");
        mockMvc.perform(post("/api/drugs/barcode/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barcode\":\"6901234567890\"}"))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("条码未收录"));
    }

    @Test
    @DisplayName("扫到的条码能直接映射回字典项")
    void knownBarcodeResolvesToDict() throws Exception {
        String token = registerAndLogin("father14");
        long drugId = jdbcTemplate.queryForObject(
                "SELECT id FROM drug_dict WHERE generic_name = '布洛芬片' AND deleted = 0", Long.class);
        jdbcTemplate.update("INSERT INTO drug_barcode (barcode, drug_dict_id) VALUES (?, ?)", "6901112223334", drugId);

        JsonNode resolved = postJson(token, "/api/drugs/barcode/resolve", "{\"barcode\":\"6901112223334\"}");
        assertThat(resolved.path("genericName").asText()).isEqualTo("布洛芬片");
    }

    @Test
    @DisplayName("① 建药 → 事件 → ② 归一化落 drug_component，评估时诚实报「含量待录入」")
    void drugCreatedEventIsNormalizedByModuleTwo() throws Exception {
        String token = registerAndLogin("mother13");
        long memberId = memberOf(token);
        seedIbuprofenComponent();

        JsonNode created = postJson(token, "/api/drugs", """
                {"genericName":"布洛芬缓释胶囊","dosageForm":"缓释胶囊","specification":"0.3g","otcRx":"OTC"}""");
        long drugDictId = created.path("drugDictId").asLong();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM drug_component WHERE drug_dict_id = ?", Integer.class, drugDictId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT resolve_status FROM drug_component WHERE drug_dict_id = ?", String.class, drugDictId))
                .isEqualTo("PENDING");

        ruleSetProvider.invalidate();
        JsonNode report = postJson(token, "/api/risk/evaluate", """
                {"memberId":%d,"scenario":"REGIMEN_ADD","items":[{"drugDictId":%d,"doseAmount":1,"timesPerDay":2}]}"""
                .formatted(memberId, drugDictId));

        assertThat(report.path("incompleteReasons").toString()).contains("待人工录入");
        assertThat(report.path("blocking").asBoolean()).isFalse();
    }

    // ---- helper ------------------------------------------------------------

    private JsonNode suggest(String token, String keyword) throws Exception {
        String body = mockMvc.perform(get("/api/drugs/suggest").param("kw", keyword)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    private void seedIbuprofenComponent() {
        jdbcTemplate.update("""
                INSERT INTO component_dict (component_code, standard_name, dose_unit, therapeutic_group)
                VALUES ('IBUPROFEN', '布洛芬', 'mg', 'NSAID')""");
        ruleSetProvider.invalidate();
    }

    private long memberOf(String token) throws Exception {
        String body = mockMvc.perform(get("/api/family").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("members").get(0).path("memberId").asLong();
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\",\"nickname\":\""
                                + username + "\"}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private JsonNode postJson(String token, String path, String payload) throws Exception {
        String body = mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}

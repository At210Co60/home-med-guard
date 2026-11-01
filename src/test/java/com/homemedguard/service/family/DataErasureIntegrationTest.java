package com.homemedguard.service.family;

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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "删除我的全部数据"：① 删自己那几张贴，② 的数据由注销事件让 ② 自己物理清。
 * 这里不加 @Transactional——要看的正是提交后的落库结果。
 */
@SpringBootTest(properties = {"app.module.family=true", "app.module.drug=true", "app.module.rule=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M1 账号注销与跨模块清理")
class DataErasureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RuleSetProvider ruleSetProvider;

    @Test
    @DisplayName("密码不对不删；删完之后账号、家庭、自建药、② 的留痕都不在了")
    void erasureRemovesOwnedDataAcrossModules() throws Exception {
        String token = registerAndLogin("erase_user");
        long memberId = memberId(token);
        long userId = userId("erase_user");
        long familyId = getJson(token, "/api/family").path("familyId").asLong();

        long drugId = postJson(token, "/api/drugs", """
                {"genericName":"测试注销用药","dosageForm":"片剂","specification":"1片","otcRx":"OTC"}""")
                .path("drugDictId").asLong();
        seedComponentAndMapping("ERASE_COMPONENT", "测试注销成分", drugId);

        // 先产生一条 ② 的评估留痕
        postJson(token, "/api/risk/evaluate", """
                {"memberId":%d,"scenario":"REGIMEN_ADD","items":[
                  {"componentRef":"测试注销成分","doseAmount":100,"doseUnit":"mg","timesPerDay":1}]}"""
                .formatted(memberId));
        assertThat(count("SELECT COUNT(*) FROM risk_evaluation_log WHERE member_id = " + memberId)).isPositive();
        assertThat(count("SELECT COUNT(*) FROM drug_component WHERE drug_dict_id = " + drugId)).isEqualTo(1);

        mockMvc.perform(delete("/api/me/data").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"WrongPass1!\"}"))
                .andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM risk_evaluation_log WHERE member_id = " + memberId)).isPositive();

        JsonNode report = deleteJson(token, "{\"password\":\"Passw0rd!\"}");
        assertThat(report.path("familyDeleted").asBoolean()).isTrue();
        assertThat(report.path("deletedMembers").asInt()).isEqualTo(1);
        assertThat(report.path("deletedDrugs").asInt()).isEqualTo(1);
        assertThat(report.path("note").asText()).contains("整户删除");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"erase_user\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(count("SELECT COUNT(*) FROM risk_evaluation_log WHERE member_id = " + memberId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM drug_component WHERE drug_dict_id = " + drugId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM drug_dict WHERE id = " + drugId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM family_member WHERE id = " + memberId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM family WHERE id = " + familyId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM refresh_token WHERE user_id = " + userId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM sys_user WHERE id = " + userId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'ACCOUNT_ERASE'", Integer.class)).isPositive();
    }

    // ---- helper ------------------------------------------------------------

    private void seedComponentAndMapping(String code, String standardName, long drugId) {
        jdbcTemplate.update("""
                INSERT INTO component_dict (component_code, standard_name, dose_unit, therapeutic_group)
                VALUES (?, ?, 'mg', 'TEST')""", code, standardName);
        Long componentId = jdbcTemplate.queryForObject(
                "SELECT id FROM component_dict WHERE component_code = ?", Long.class, code);
        jdbcTemplate.update("""
                INSERT INTO drug_component (drug_dict_id, component_id, content_per_form, form_unit, resolve_status)
                VALUES (?, ?, 500, '片', 'CONFIRMED')""", drugId, componentId);
        ruleSetProvider.invalidate();
    }

    private long userId(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, username);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\",\"nickname\":\"注销测试\"}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private long memberId(String token) throws Exception {
        return getJson(token, "/api/family").path("members").get(0).path("memberId").asLong();
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

    private JsonNode deleteJson(String token, String payload) throws Exception {
        String body = mockMvc.perform(delete("/api/me/data").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}

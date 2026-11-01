package com.homemedguard.service.drug;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homemedguard.common.storage.FileStorageService;
import com.homemedguard.service.rule.core.RuleSetProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 说明书图片上传 + 要点结构化 + 命中 A 级时随报告下发应急指引。
 */
@SpringBootTest(properties = {
        "app.module.family=true", "app.module.drug=true", "app.module.rule=true",
        "app.auth.bootstrap.username=leaflet_admin", "app.auth.bootstrap.password=Passw0rd!"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M1 附件 + M3 说明书要点")
class AttachmentLeafletIntegrationTest {

    private static final byte[] PNG_BYTES = new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RuleSetProvider ruleSetProvider;

    @Autowired
    private FileStorageService fileStorage;

    @Test
    @DisplayName("上传说明书图片 → 列表可见 → 内容原样读回")
    void uploadListAndDownload() throws Exception {
        String admin = login("leaflet_admin");
        long drugId = createDictDrug(admin, "对乙酰氨基酚片");

        String uploadBody = mockMvc.perform(multipart("/api/drugs/" + drugId + "/attachments")
                        .file(new MockMultipartFile("file", "说明书 第1页.png", "image/png", PNG_BYTES))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode uploaded = objectMapper.readTree(uploadBody).path("data");

        long attachmentId = uploaded.path("id").asLong();
        assertThat(uploaded.path("originalName").asText()).isEqualTo("说明书 第1页.png");
        assertThat(uploaded.path("sizeBytes").asLong()).isEqualTo(PNG_BYTES.length);

        assertThat(getJson(admin, "/api/drugs/" + drugId + "/attachments")).hasSize(1);

        mockMvc.perform(get("/api/drug-attachments/" + attachmentId + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(PNG_BYTES));
    }

    @Test
    @DisplayName("非白名单类型直接拒；存储 key 不合法也拒（防目录穿越）")
    void rejectsBadExtensionAndTraversalKeys() throws Exception {
        String admin = login("leaflet_admin");
        long drugId = createDictDrug(admin, "测试拒收用药");

        mockMvc.perform(multipart("/api/drugs/" + drugId + "/attachments")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("只支持"));

        assertThatThrownBy(() -> fileStorage.load("../../etc/passwd"))
                .isInstanceOf(com.homemedguard.common.error.BizException.class);
        assertThatThrownBy(() -> fileStorage.load("2026/11/notavalidkey.png"))
                .isInstanceOf(com.homemedguard.common.error.BizException.class);
    }

    @Test
    @DisplayName("要点必须带来源；命中 A 级风险时报告里带出应急指引，普通成员不能录要点")
    void leafletDrivesEmergencyGuidesAndIsAdminOnly() throws Exception {
        String admin = login("leaflet_admin");
        seedComponent("ACETAMINOPHEN", "对乙酰氨基酚");
        seedComponent("WARFARIN", "华法林");

        mockMvc.perform(post("/api/leaflets").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentCode\":\"ACETAMINOPHEN\",\"sectionCode\":\"OVERDOSE_ACTION\","
                                + "\"content\":\"测试要点：立即就医并带上药盒\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("必须带来源"));

        postJson(admin, "/api/leaflets", """
                {"componentCode":"ACETAMINOPHEN","sectionCode":"OVERDOSE_ACTION",
                 "content":"测试要点：立即就医并带上药盒","sourceRef":"测试来源（非真实说明书）","sourceDate":"2026-11-01"}""");
        postJson(admin, "/api/leaflets", """
                {"componentCode":"ACETAMINOPHEN","sectionCode":"SEE_DOCTOR","content":"测试：出现皮疹立即停药",
                 "sourceRef":"测试来源（非真实说明书）","sourceDate":"2026-11-01"}""");

        JsonNode sections = getJson(admin, "/api/leaflets/ACETAMINOPHEN");
        assertThat(sections.toString()).contains("OVERDOSE_ACTION").contains("立即就医并带上药盒");

        String member = registerAndLogin("leaflet_member");
        mockMvc.perform(post("/api/leaflets").header(HttpHeaders.AUTHORIZATION, "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentCode\":\"ACETAMINOPHEN\",\"sectionCode\":\"ADVERSE\","
                                + "\"content\":\"x\",\"sourceRef\":\"y\",\"sourceDate\":\"2026-11-01\"}"))
                .andExpect(status().isForbidden());

        publishInteractionRule(admin, "INT-GUIDE-001");
        long memberId = getJson(member, "/api/family").path("members").get(0).path("memberId").asLong();
        JsonNode report = postJson(member, "/api/risk/evaluate", """
                {"memberId":%d,"scenario":"PRESCRIBE","items":[
                  {"componentRef":"对乙酰氨基酚","doseAmount":500,"doseUnit":"mg","timesPerDay":3},
                  {"componentRef":"华法林","doseAmount":5,"doseUnit":"mg","timesPerDay":1}]}"""
                .formatted(memberId));

        assertThat(report.path("blocking").asBoolean()).isTrue();
        assertThat(report.path("emergencyGuides").toString())
                .contains("OVERDOSE_ACTION").contains("立即就医并带上药盒").contains("测试来源");
    }

    // ---- helper ------------------------------------------------------------

    private void seedComponent(String code, String standardName) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM component_dict WHERE component_code = ?", Integer.class, code);
        if (exists != null && exists > 0) {
            ruleSetProvider.invalidate();
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO component_dict (component_code, standard_name, dose_unit, therapeutic_group)
                VALUES (?, ?, 'mg', 'TEST')""", code, standardName);
        ruleSetProvider.invalidate();
    }

    private void publishInteractionRule(String admin, String ruleCode) throws Exception {
        postJson(admin, "/api/admin/rules/interaction", """
                {"ruleCode":"%s","componentA":"ACETAMINOPHEN","componentB":"WARFARIN","severity":"A",
                 "advice":"测试建议：请遵医嘱","sourceRef":"测试来源（非真实说明书）","sourceDate":"2026-11-01"}"""
                .formatted(ruleCode));
        postJson(admin, "/api/admin/rules/interaction/" + ruleCode + "/1/submit", "{}");
        postJson(admin, "/api/admin/rules/interaction/" + ruleCode + "/1/approve", "{}");
    }

    private long createDictDrug(String admin, String genericName) throws Exception {
        return postJson(admin, "/api/drugs", """
                {"genericName":"%s","dosageForm":"片剂","specification":"500mg","otcRx":"OTC"}"""
                .formatted(genericName)).path("drugDictId").asLong();
    }

    private String login(String username) throws Exception {
        return token(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\",\"nickname\":\"成员\"}"))
                .andExpect(status().isOk());
        return login(username);
    }

    private String token(String body) throws Exception {
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

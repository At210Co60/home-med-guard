package com.homemedguard.service.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homemedguard.common.contract.MemberProfileQueryFacade;
import com.homemedguard.service.auth.entity.SysUserEntity;
import com.homemedguard.service.auth.mapper.SysUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M1 主流程：注册即建个人家庭 → 登录 → 邀请码加入 → 建档（加密）→ 越权拦截 → 交给 ② 评估。
 * 三个模块一起开，验证 ①② 的契约真能接通，而不是各自单测里的替身。
 */
@SpringBootTest(properties = {"app.module.family=true", "app.module.drug=true", "app.module.rule=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("M1 账号·家庭·档案 端到端")
class AuthFamilyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("注册自动建个人家庭，本人是 FAMILY_ADMIN 且有档案行")
    void registerCreatesPersonalFamily() throws Exception {
        String token = registerAndLogin("father01", "Passw0rd!", "爸爸");

        JsonNode family = getJson(token, "/api/family");
        assertThat(family.path("myRole").asText()).isEqualTo("FAMILY_ADMIN");
        assertThat(family.path("members")).hasSize(1);
        assertThat(family.path("members").get(0).path("profileReady").asBoolean()).isTrue();
        assertThat(family.path("members").get(0).path("displayName").asText()).isEqualTo("爸爸");
    }

    @Test
    @DisplayName("用户名重复 409；密码错与用户不存在给同一句提示")
    void rejectsDuplicateAndBadCredentials() throws Exception {
        register("father02", "Passw0rd!", null);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"father02\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isConflict());

        String wrongPassword = messageOf(loginRaw("father02", "wrong-pass"));
        String unknownUser = messageOf(loginRaw("nobody_here", "wrong-pass"));
        assertThat(unknownUser).isEqualTo(wrongPassword).contains("用户名或密码不正确");
    }

    @Test
    @DisplayName("refresh 换 token 后旧 refresh token 立即作废")
    void refreshTokenRotates() throws Exception {
        register("father03", "Passw0rd!", null);
        JsonNode first = objectMapper.readTree(loginRaw("father03", "Passw0rd!")).path("data");
        assertThat(first.path("accessToken").asText()).isNotBlank();

        JsonNode second = objectMapper.readTree(refreshRaw(first.path("refreshToken").asText())).path("data");
        assertThat(second.path("accessToken").asText()).isNotBlank();
        assertThat(second.path("refreshToken").asText()).isNotEqualTo(first.path("refreshToken").asText());

        // 旧 token 再用一次必须失败
        assertThat(objectMapper.readTree(refreshRaw(first.path("refreshToken").asText())).path("code").asInt())
                .isEqualTo(40100);
    }

    @Test
    @DisplayName("未登录访问家庭接口给 401，body 是统一错误码")
    void anonymousCannotReadFamily() throws Exception {
        mockMvc.perform(get("/api/family"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("\"code\":40100"));
    }

    @Test
    @DisplayName("邀请码：加入成功 40100 之外的人不能重复加入，错码 404")
    void inviteFlowEnforcesSingleFamily() throws Exception {
        String adminToken = registerAndLogin("admin04", "Passw0rd!", null);
        String invite = postJson(adminToken, "/api/family/invite", "{}").path("code").asText();
        assertThat(invite).hasSize(8);

        String kidToken = registerAndLogin("kid04", "Passw0rd!", "儿子");
        JsonNode joined = postJson(kidToken, "/api/family/join", "{\"code\":\"" + invite + "\"}");
        assertThat(joined.path("familyId").asLong()).isPositive();

        String otherToken = registerAndLogin("admin04b", "Passw0rd!", null);
        String otherInvite = postJson(otherToken, "/api/family/invite", "{}").path("code").asText();
        postExpectStatus(kidToken, "/api/family/join", "{\"code\":\"" + otherInvite + "\"}", 409);
        postExpectStatus(adminToken, "/api/family/join", "{\"code\":\"ZZZZZZZZ\"}", 404);
    }

    @Test
    @DisplayName("过敏史与慢病在库里是密文，接口才返回明文；成员列表不带原文")
    void healthColumnsAreEncryptedAtRest() throws Exception {
        String token = registerAndLogin("mother05", "Passw0rd!", "妈妈");
        long memberId = getJson(token, "/api/family").path("members").get(0).path("memberId").asLong();

        putJson(token, "/api/family/members/" + memberId + "/profile", """
                {"displayName":"妈妈","birthDate":"1992-03-01","gender":"F","weightKg":55.5,
                 "tagCodes":["PREGNANT"],"allergyHistory":"青霉素、头孢类","chronicConditions":"高血压",
                 "liverGrade":"A","renalEgfr":90}""");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT allergy_history_enc FROM family_member_profile WHERE member_id = ?", String.class, memberId))
                .startsWith("enc:v1:").doesNotContain("青霉素");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT chronic_conditions_enc FROM family_member_profile WHERE member_id = ?", String.class, memberId))
                .startsWith("enc:v1:").doesNotContain("高血压");

        JsonNode detail = getJson(token, "/api/family/members/" + memberId + "/profile");
        assertThat(detail.path("allergyHistory").asText()).isEqualTo("青霉素、头孢类");
        assertThat(detail.path("tagCodes")).hasSize(1);

        assertThat(getJson(token, "/api/family").path("members").get(0).toString())
                .doesNotContain("青霉素").doesNotContain("高血压");
    }

    @Test
    @DisplayName("别人家的成员读不到：越权按不存在处理")
    void crossFamilyAccessIsDenied() throws Exception {
        String motherToken = registerAndLogin("mother06", "Passw0rd!", "妈妈");
        long memberId = getJson(motherToken, "/api/family").path("members").get(0).path("memberId").asLong();

        String strangerToken = registerAndLogin("stranger06", "Passw0rd!", "邻居");
        mockMvc.perform(get("/api/family/members/" + memberId + "/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("① 接上之后 ② 不再报 503：过敏史原文也能对上成分")
    void moduleOneFeedsRiskEvaluation() throws Exception {
        String token = registerAndLogin("mother07", "Passw0rd!", "孕妇");
        long memberId = getJson(token, "/api/family").path("members").get(0).path("memberId").asLong();
        putJson(token, "/api/family/members/" + memberId + "/profile", """
                {"displayName":"孕妇","birthDate":"1992-03-01","weightKg":55,
                 "tagCodes":["PREGNANT"],"allergyHistory":"布洛芬"}""");

        assertThat(context.getBeanProvider(MemberProfileQueryFacade.class).getIfAvailable()).isNotNull();

        seedIbuprofenRules();
        JsonNode report = postJson(token, "/api/risk/evaluate", """
                {"memberId":%d,"scenario":"REGIMEN_ADD","items":[
                  {"componentRef":"布洛芬","doseAmount":400,"doseUnit":"mg","timesPerDay":3}]}"""
                .formatted(memberId));

        assertThat(report.path("maxSeverity").asText()).isEqualTo("A");
        assertThat(report.path("blocking").asBoolean()).isTrue();
        assertThat(report.path("items").toString()).contains("IBUPROFEN");
    }

    @Test
    @DisplayName("口令用 BCrypt cost 10 存，库里拿不到原文")
    void passwordIsHashed() throws Exception {
        register("father08", "Passw0rd!", null);
        SysUserEntity user = sysUserMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getUsername, "father08"));
        assertThat(user.getPasswordHash()).startsWith("$2a$10$").doesNotContain("Passw0rd!");
    }

    // ---- helper ------------------------------------------------------------

    private void seedIbuprofenRules() {
        jdbcTemplate.update("""
                INSERT INTO component_dict (component_code, standard_name, dose_unit, therapeutic_group)
                VALUES ('IBUPROFEN', '布洛芬', 'mg', 'NSAID')""");
        jdbcTemplate.update("""
                INSERT INTO contraindication_rule (rule_code, version, status, component, tag_code, severity,
                    advice, source_ref, source_date)
                VALUES ('CON-IT-001', 1, 'PUBLISHED', 'IBUPROFEN', 'PREGNANT', 'A', '孕晚期禁用非甾体抗炎药',
                    '测试来源（非真实说明书）', DATE '2026-11-01')""");
    }

    private void register(String username, String password, String nickname) throws Exception {
        String payload = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\""
                + (nickname == null ? "" : ",\"nickname\":\"" + nickname + "\"") + "}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
    }

    private String registerAndLogin(String username, String password, String nickname) throws Exception {
        register(username, password, nickname);
        return objectMapper.readTree(loginRaw(username, password)).path("data").path("accessToken").asText();
    }

    private String messageOf(String body) throws Exception {
        return objectMapper.readTree(body).path("message").asText();
    }

    private String loginRaw(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String refreshRaw(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
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

    private void postExpectStatus(String token, String path, String payload, int status) throws Exception {
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(status));
    }

    private JsonNode putJson(String token, String path, String payload) throws Exception {
        String body = mockMvc.perform(put(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}

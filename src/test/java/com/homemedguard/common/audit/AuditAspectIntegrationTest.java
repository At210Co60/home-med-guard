package com.homemedguard.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审计链路：写操作异步落 audit_log，且口令与健康原文一律不进审计表。
 * 这里刻意不加 @Transactional——审计在另一个线程里提交，回滚的测试事务会把它挡在视野外。
 */
@SpringBootTest(properties = {"app.module.family=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("审计日志与脱敏")
class AuditAspectIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("注册与登录都留痕，口令一律被替换")
    void authWritesAreAuditedWithMaskedPassword() throws Exception {
        int before = countOf("AUTH_REGISTER");
        register("audit_user_a");
        assertThat(awaitRows("AUTH_REGISTER", before)).isPositive();

        String registerPayload = latestPayload("AUTH_REGISTER");
        assertThat(registerPayload).contains("***").doesNotContain("Passw0rd!");

        before = countOf("AUTH_LOGIN");
        login("audit_user_a");
        assertThat(awaitRows("AUTH_LOGIN", before)).isPositive();
        assertThat(latestPayload("AUTH_LOGIN")).contains("***").doesNotContain("Passw0rd!");
    }

    @Test
    @DisplayName("改档案会留痕，但过敏史原文不进审计表，操作人可追")
    void profileUpdateIsAuditedWithoutHealthPayload() throws Exception {
        String token = registerAndLogin("audit_user_b");
        long memberId = memberId(token);
        int before = countOf("MEMBER_PROFILE_UPDATE");

        mockMvc.perform(put("/api/family/members/" + memberId + "/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"妈妈\",\"allergyHistory\":\"青霉素\",\"chronicConditions\":\"高血压\"}"))
                .andExpect(status().isOk());

        assertThat(awaitRows("MEMBER_PROFILE_UPDATE", before)).isPositive();
        String payload = jdbcTemplate.queryForObject(
                "SELECT before_json || after_json FROM audit_log WHERE action = 'MEMBER_PROFILE_UPDATE' ORDER BY id DESC LIMIT 1",
                String.class);
        assertThat(payload).doesNotContain("青霉素").doesNotContain("高血压").contains("***");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT operator_user_id FROM audit_log WHERE action = 'MEMBER_PROFILE_UPDATE' ORDER BY id DESC LIMIT 1",
                Long.class)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ip FROM audit_log WHERE action = 'MEMBER_PROFILE_UPDATE' ORDER BY id DESC LIMIT 1",
                String.class)).isNotBlank();
    }

    @Test
    @DisplayName("失败的写操作也留痕，便于排查越权尝试")
    void failedWritesAreAuditedToo() throws Exception {
        String token = registerAndLogin("audit_user_c");
        int before = countOf("MEMBER_PROFILE_UPDATE");

        mockMvc.perform(put("/api/family/members/999999/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"不存在\"}"))
                .andExpect(status().isNotFound());

        assertThat(awaitRows("MEMBER_PROFILE_UPDATE", before)).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT after_json FROM audit_log WHERE action = 'MEMBER_PROFILE_UPDATE' ORDER BY id DESC LIMIT 1",
                String.class)).contains("BizException");
    }

    // ---- helper ------------------------------------------------------------

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\",\"nickname\":\"审计\"}"))
                .andExpect(status().isOk());
    }

    private String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private String registerAndLogin(String username) throws Exception {
        register(username);
        return login(username);
    }

    private long memberId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/family").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("members").get(0).path("memberId").asLong();
    }

    private int countOf(String action) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ?", Integer.class, action);
    }

    private String latestPayload(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT before_json || after_json FROM audit_log WHERE action = ? ORDER BY id DESC LIMIT 1",
                String.class, action);
    }

    /** 审计是异步旁路：轮询等到新增行落库，超时就断言失败而不是静默放过。 */
    private int awaitRows(String action, int countBefore) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        int delta = countOf(action) - countBefore;
        while (delta <= 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            delta = countOf(action) - countBefore;
        }
        return delta;
    }
}

package com.homemedguard.service.rule;

import com.homemedguard.service.rule.api.RiskEvaluateFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ② 开着、① 还没接入时的行为：接口在、能启动，但没有成员档案就明确拒绝给结论。
 * 这条是"主要功能在后端 + 模块互不影响"的核心证据。
 */
@SpringBootTest(properties = "app.module.rule=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "tester", roles = "MEMBER")
@DisplayName("② 在、① 缺席")
class RiskEvaluateWithoutModuleOneTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("引擎 bean 存在，缺 ① 时返回 503 而不是假的「无风险」")
    void refusesToEvaluateWithoutMemberProfile() throws Exception {
        assertThat(context.getBeanProvider(RiskEvaluateFacade.class).getIfAvailable()).isNotNull();

        mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":10,"scenario":"REGIMEN_ADD","items":[
                                  {"componentRef":"acetaminophen","doseAmount":500,"doseUnit":"mg","timesPerDay":3}]}"""))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(503))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("\"code\":50300")
                        .contains("① 成员档案模块未接入"));
    }

    @Test
    @DisplayName("请求体不是合法 JSON 给 400，不占成 500")
    void malformedJsonIsClientError() throws Exception {
        mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\": \"oops"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(400))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("\"code\":40001"));
    }
}

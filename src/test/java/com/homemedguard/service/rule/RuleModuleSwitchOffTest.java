package com.homemedguard.service.rule;

import com.homemedguard.common.contract.MemberProfileQueryFacade;
import com.homemedguard.service.rule.api.RiskEvaluateFacade;
import com.homemedguard.service.rule.api.SpacingRuleQueryFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 模块化铁律的反证：test profile 里 app.module.rule=false。
 * ② 整块 Bean 消失、接口下线，但系统照样启动、① 的自检接口照常——这才叫互不影响。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("② 关掉开关后系统仍能启动")
class RuleModuleSwitchOffTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("规则引擎与两个 Facade 都不在容器里")
    void ruleBeansAreAbsent() {
        assertThat(context.getBeanProvider(RiskEvaluateFacade.class).getIfAvailable()).isNull();
        assertThat(context.getBeanProvider(SpacingRuleQueryFacade.class).getIfAvailable()).isNull();
        assertThat(context.getBeanProvider(MemberProfileQueryFacade.class).getIfAvailable()).isNull();
    }

    @Test
    @DisplayName("评估接口下线：请求落到 404，不是 500")
    void evaluateEndpointIsOffline() throws Exception {
        mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":10,\"items\":[{\"componentRef\":\"ACETAMINOPHEN\","
                                + "\"doseAmount\":500,\"timesPerDay\":1}]}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));
    }
}

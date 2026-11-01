package com.homemedguard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homemedguard.common.setting.SettingService;
import com.homemedguard.common.setting.SysConfigEntity;
import com.homemedguard.common.setting.SysConfigMapper;
import com.homemedguard.common.trace.TraceId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 骨架验收：没有 MySQL、没有任何业务模块实现，系统照样启动，
 * 且模块开关、参数家庭级覆盖、链路号都能当场验证（CONTRIBUTING §6.8 的证据）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeMedGuardSkeletonTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SettingService settingService;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Test
    void modulesEndpointReportsSwitchesAndUnwiredContracts() throws Exception {
        String body = mockMvc.perform(get("/api/modules"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(body).path("data");

        assertThat(textList(data.path("disabledModules"))).contains("rule", "schedule");
        assertThat(textList(data.path("enabledModules"))).contains("stock", "expiry");
        assertThat(textList(data.path("unwiredContracts")))
                .contains("MemberProfileQueryFacade", "DrugQueryFacade");
    }

    @Test
    void traceIdIsIssuedOnEveryResponse() throws Exception {
        String traceId = mockMvc.perform(get("/api/modules")
                        .header(TraceId.HEADER, "ok-TRACE_1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(TraceId.HEADER);
        assertThat(traceId).isEqualTo("ok-TRACE_1");

        String generated = mockMvc.perform(get("/api/modules").contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getHeader(TraceId.HEADER);
        assertThat(generated).hasSize(16);
    }

    @Test
    @Transactional
    void familyOverrideWinsOverGlobalDefault() {
        assertThat(settingService.intOf("expiry.warn.days", 999L, -1)).isEqualTo(180);

        SysConfigEntity override = new SysConfigEntity();
        override.setConfigKey("expiry.warn.days");
        override.setConfigValue("30");
        override.setValueType("INT");
        override.setFamilyId(7L);
        sysConfigMapper.insert(override);
        settingService.invalidateAll();

        assertThat(settingService.intOf("expiry.warn.days", 7L, -1)).isEqualTo(30);
        assertThat(settingService.intOf("expiry.warn.days", 8L, -1)).isEqualTo(180);
        assertThat(settingService.boolOf("risk.confirm.blocking", 7L, false)).isTrue();
        assertThat(settingService.intOf("not.exist.key", 7L, 42)).isEqualTo(42);
    }

    private static List<String> textList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}

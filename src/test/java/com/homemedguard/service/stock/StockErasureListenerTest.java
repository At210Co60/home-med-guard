package com.homemedguard.service.stock;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homemedguard.service.expiry.entity.ActionTaskEntity;
import com.homemedguard.service.expiry.mapper.ActionTaskMapper;
import com.homemedguard.service.stock.entity.DisposalRecordEntity;
import com.homemedguard.service.stock.entity.FamilyDrugEntity;
import com.homemedguard.service.stock.entity.StockBatchEntity;
import com.homemedguard.service.stock.entity.StorageLocationEntity;
import com.homemedguard.service.stock.mapper.DisposalRecordMapper;
import com.homemedguard.service.stock.mapper.FamilyDrugMapper;
import com.homemedguard.service.stock.mapper.StockBatchMapper;
import com.homemedguard.service.stock.mapper.StorageLocationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ① 的"删除我的全部数据"与 ② 的表之间怎么接上：注销只广播事件，
 * 批次/在册药/台账/位置/任务各由本模块自己物理清（表归属唯一）。
 */
@SpringBootTest(properties = {"app.module.family=true", "app.module.drug=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StockErasureListenerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StockBatchMapper batches;

    @Autowired
    private FamilyDrugMapper drugs;

    @Autowired
    private DisposalRecordMapper disposals;

    @Autowired
    private StorageLocationMapper locations;

    @Autowired
    private ActionTaskMapper tasks;

    private String token;
    private Long familyId;

    @BeforeEach
    void signIn() throws Exception {
        token = registerAndLogin("爸爸");
        familyId = myFamilyId(token);
    }

    @Test
    @DisplayName("整户注销：库存侧五张表一行不剩")
    void erasesEverythingWhenTheFamilyIsGone() throws Exception {
        stockDataWithNearExpiryBatch();

        assertThat(counts()[0]).isPositive();
        assertThat(counts()[4]).isPositive();

        erase();

        long[] after = counts();
        assertThat(after).containsExactly(0, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("只是成员退出：家庭还在，药箱数据一行都不能动")
    void keepsFamilyDataWhenOnlyOneMemberLeaves() throws Exception {
        String kidToken = registerAndLogin("儿子");
        String code = inviteCode(token);
        join(kidToken, code);
        stockDataWithNearExpiryBatch();

        long[] before = counts();
        eraseWith(kidToken);

        assertThat(counts()).containsExactly(before);
    }

    /** 在册药 + 批次 + 位置 + 处置台账 + 一条临期任务。 */
    private void stockDataWithNearExpiryBatch() throws Exception {
        Long location = jsonBody(post("/api/stock/locations"), "{\"name\":\"客厅药柜\"}").path("id").asLong();
        Long drug = jsonBody(post("/api/stock/drugs"), "{\"genericName\":\"阿司匹林肠溶片\","
                + "\"dosageForm\":\"片剂\"}").path("id").asLong();
        LocalDate today = LocalDate.now();
        jsonBody(post("/api/stock/batches"), "{\"familyDrugId\":" + drug + ",\"batchNo\":\"B-1\","
                + "\"expiryDate\":\"" + today.plusDays(10) + "\",\"quantity\":5,\"purchasePrice\":12.5,"
                + "\"purchaseDate\":\"" + today + "\",\"locationId\":" + location + "}");
        jsonBody(post("/api/expiry/scan?bizDate=" + today), null);
        Long batchId = batches.selectList(Wrappers.<StockBatchEntity>lambdaQuery()
                        .eq(StockBatchEntity::getFamilyId, familyId))
                .get(0).getId();
        jsonBody(post("/api/stock/dispose"), "{\"batchId\":" + batchId + ",\"action\":\"DISPOSED\","
                + "\"reason\":\"测试清理\"}");
    }

    /** @return [批次, 在册药, 位置, 处置台账, 待处理任务] */
    private long[] counts() {
        return new long[]{
                batches.selectCount(Wrappers.<StockBatchEntity>lambdaQuery()
                        .eq(StockBatchEntity::getFamilyId, familyId)),
                drugs.selectCount(Wrappers.<FamilyDrugEntity>lambdaQuery()
                        .eq(FamilyDrugEntity::getFamilyId, familyId)),
                locations.selectCount(Wrappers.<StorageLocationEntity>lambdaQuery()
                        .eq(StorageLocationEntity::getFamilyId, familyId)),
                disposals.selectCount(Wrappers.<DisposalRecordEntity>lambdaQuery()
                        .eq(DisposalRecordEntity::getFamilyId, familyId)),
                tasks.selectCount(Wrappers.<ActionTaskEntity>lambdaQuery()
                        .eq(ActionTaskEntity::getFamilyId, familyId))
        };
    }

    private void erase() throws Exception {
        eraseWith(token);
    }

    private void eraseWith(String jwt) throws Exception {
        mockMvc.perform(withToken(delete("/api/me/data"), jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk());
    }

    private String inviteCode(String jwt) throws Exception {
        return objectMapper.readTree(mockMvc.perform(withToken(post("/api/family/invite"), jwt)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("data").path("code").asText();
    }

    private void join(String jwt, String code) throws Exception {
        mockMvc.perform(withToken(post("/api/family/join"), jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());
    }

    private String registerAndLogin(String nickname) throws Exception {
        String username = "er" + Math.abs(System.nanoTime() % 10000000);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\","
                                + "\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private Long myFamilyId(String jwt) throws Exception {
        String body = mockMvc.perform(withToken(get("/api/family"), jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("familyId").asLong();
    }

    private JsonNode jsonBody(MockHttpServletRequestBuilder request, String body) throws Exception {
        MockHttpServletRequestBuilder withAuth = withToken(request, token);
        if (body != null) {
            withAuth = withAuth.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult result = mockMvc.perform(withAuth).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String jwt) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
    }
}

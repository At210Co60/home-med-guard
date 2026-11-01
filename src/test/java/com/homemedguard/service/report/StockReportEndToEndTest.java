package com.homemedguard.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B 的部分端到端：注册登录 → 入库 → 开封 → FEFO 出库 → 扫描出任务 → 处置 → 任务被事件关掉
 * → 报表与导出，外加越权与未登录两条守卫用例。
 * 打开 ① 的 family 模块，才能验出"familyId 由登录态解析"这条链路真的生效。
 */
@SpringBootTest(properties = {"app.module.family=true", "app.module.drug=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StockReportEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private Long familyId;

    @BeforeEach
    void signIn() throws Exception {
        String[] session = registerAndLogin();
        token = session[0];
        familyId = Long.valueOf(session[1]);
    }

    @Test
    @DisplayName("入库→开封→出库→扫描→处置→报表，一条链跑到底")
    void walksTheWholeM2M6Chain() throws Exception {
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth lastMonth = thisMonth.minusMonths(1);

        Long drug = registerDrug();
        Long nearBatch = intake(drug, "B-NEAR", today.plusDays(10), 6, "30.00", today);
        intake(drug, "B-LAST", today.plusDays(400), 4, "10.00", lastMonth.atDay(1));

        // 开封让第二轨压线：滴眼液 28 天，25 天前开的封
        open(nearBatch, today.minusDays(25));
        consume(drug, 2);
        json(post("/api/expiry/scan?bizDate=" + today));

        JsonNode tasks = json(get("/api/expiry/tasks"));
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).path("level").asText()).isEqualTo("HIGH");
        assertThat(tasks.get(0).path("taskType").asText()).isEqualTo("EXPIRY_NEAR");
        assertThat(tasks.get(0).path("title").asText()).contains("左氧氟沙星滴眼液");

        dispose(nearBatch, "DISPOSED");

        // 处置事件收尾任务：库存模块没有直接写 action_task，靠 stock -> expiry 的事件
        assertThat(json(get("/api/expiry/tasks"))).isEmpty();

        JsonNode waste = json(get("/api/report/waste?startYm=" + ym(lastMonth) + "&endYm=" + ym(thisMonth)));
        // 购入 30×6 + 10×4 = 220；丢弃时剩 4 支 × 30 = 120
        assertDecimal(waste.path("totalPurchased"), "220");
        assertDecimal(waste.path("totalWasted"), "120");
        assertDecimal(waste.path("totalRatePercent"), "54.55");
        assertThat(waste.path("months")).hasSize(2);
        assertThat(waste.path("caliber").asText()).contains("按购入价");
        // 报表的家庭维度来自登录态，不是查询参数
        assertThat(waste.path("familyId").asLong()).isEqualTo(familyId);

        JsonNode expense = json(get("/api/report/expense?startYm=" + ym(lastMonth) + "&endYm=" + ym(thisMonth)));
        assertThat(expense.path("rows")).isNotEmpty();
        assertThat(expense.path("rows").get(0).path("category").asText()).isEqualTo("滴眼液");

        String csv = text(get("/api/report/waste.csv?by=ALSJJF"));
        assertThat(csv).startsWith("﻿").contains("导出人：ALSJJF").contains("不构成诊疗建议");

        String markdown = text(get("/api/report/expense.md"));
        assertThat(markdown).contains("药箱支出趋势").contains("| 剂型 |");

        // 另一笔批次没被这条链动过，仍在可用列表里
        JsonNode batches = json(get("/api/stock/batches?familyDrugId=" + drug + "&asOf=" + today));
        assertThat(batches).hasSize(1);
        assertThat(batches.get(0).path("batchNo").asText()).isEqualTo("B-LAST");
        assertThat(batches.get(0).path("quantity").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("familyId 由登录态决定：传别人的家庭 id 一律 403，报不出别人的数据")
    void rejectsForeignFamilyId() throws Exception {
        String[] other = registerAndLogin();

        MvcResult result = mockMvc.perform(withToken(get("/api/report/waste"), other[0])
                        .param("familyId", String.valueOf(familyId)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(code(result)).isEqualTo(40300);
    }

    @Test
    @DisplayName("没带 token 一律 401，库存与报表不是公开接口")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/stock/batches?familyDrugId=1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/report/waste"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("空白通用名被 @Valid 拦在入口：400 与业务错误码，不是 500")
    void rejectsInvalidBody() throws Exception {
        MvcResult result = mockMvc.perform(withToken(post("/api/stock/drugs"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"genericName\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(code(result)).isEqualTo(40001);
    }

    @Test
    @DisplayName("同日重复扫描不再出任务，接口回 0")
    void scanIsRepeatable() throws Exception {
        LocalDate today = LocalDate.now();
        Long drug = registerDrug();
        intake(drug, "B-SCAN", today.plusDays(5), 3, "9.90", today);

        assertThat(json(post("/api/expiry/scan?bizDate=" + today)).asInt()).isEqualTo(1);
        assertThat(json(post("/api/expiry/scan?bizDate=" + today)).asInt()).isZero();
    }

    @Test
    @DisplayName("还没记账时报表返回空口径而不是报错，分母为 0 不给 0%")
    void emptyReportIsNotAnError() throws Exception {
        JsonNode waste = json(get("/api/report/waste"));

        assertDecimal(waste.path("totalPurchased"), "0");
        assertThat(waste.hasNonNull("totalRatePercent")).isFalse();

        // 全区间都没有购药记录时，CSV 每一格的浪费率都是 null：曾经在这里 500
        String csv = text(get("/api/report/waste.csv"));
        assertThat(csv).startsWith("﻿").contains("月份,购药总额,浪费金额,浪费率%");
    }

    @Test
    @DisplayName("确认看过但不处置：置 SKIPPED，未处理列表里不再出现")
    void skipRemovesFromPending() throws Exception {
        LocalDate today = LocalDate.now();
        Long drug = registerDrug();
        intake(drug, "B-SKIP", today.plusDays(7), 2, "5.00", today);
        json(post("/api/expiry/scan?bizDate=" + today));
        long taskId = json(get("/api/expiry/tasks")).get(0).path("id").asLong();

        json(post("/api/expiry/tasks/" + taskId + "/skip"));

        assertThat(json(get("/api/expiry/tasks"))).isEmpty();
    }

    @Test
    @DisplayName("处置动作填错直接拒，不落一条来路不明的台账")
    void rejectsUnknownDisposalAction() throws Exception {
        LocalDate today = LocalDate.now();
        Long drug = registerDrug();
        Long batchId = intake(drug, "B-BAD", today.plusDays(30), 2, "5.00", today);

        MvcResult result = mockMvc.perform(withToken(post("/api/stock/dispose"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchId\":" + batchId + ",\"action\":\"THROW_AWAY\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(code(result)).isEqualTo(40001);
    }

    /** @return [accessToken, familyId] */
    private String[] registerAndLogin() throws Exception {
        String username = "b" + Math.abs(System.nanoTime() % 10000000);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\","
                                + "\"nickname\":\"B 队友\"}"))
                .andExpect(status().isOk());
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(login).path("data").path("accessToken").asText();

        String family = mockMvc.perform(withToken(get("/api/family"), accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new String[]{accessToken,
                objectMapper.readTree(family).path("data").path("familyId").asText()};
    }

    private Long registerDrug() throws Exception {
        JsonNode node = jsonBody(post("/api/stock/drugs"), "{\"genericName\":\"左氧氟沙星滴眼液\","
                + "\"dosageForm\":\"滴眼液\",\"specification\":\"5ml\",\"unit\":\"支\"}");
        assertThat(node.path("source").asText()).isEqualTo("USER_INPUT");
        return node.path("id").asLong();
    }

    private Long intake(Long drug, String batchNo, LocalDate expiry, int qty, String price,
                        LocalDate purchaseDate) throws Exception {
        return jsonBody(post("/api/stock/batches"), "{\"familyDrugId\":" + drug + ",\"batchNo\":\"" + batchNo
                + "\",\"expiryDate\":\"" + expiry + "\",\"quantity\":" + qty
                + ",\"purchasePrice\":" + price + ",\"purchaseDate\":\"" + purchaseDate + "\"}")
                .path("batchId").asLong();
    }

    private void open(Long batchId, LocalDate openedDate) throws Exception {
        JsonNode node = jsonBody(post("/api/stock/batches/" + batchId + "/open"),
                "{\"openedDate\":\"" + openedDate + "\"}");
        assertThat(node.path("useByDate").asText()).isEqualTo(openedDate.plusDays(28).toString());
    }

    private void consume(Long drug, int qty) throws Exception {
        JsonNode node = jsonBody(post("/api/stock/consume"), "{\"familyDrugId\":" + drug
                + ",\"quantity\":" + qty + ",\"asOf\":\"" + LocalDate.now() + "\"}");
        assertThat(node.path("lines").get(0).path("batchNo").asText()).isEqualTo("B-NEAR");
        assertThat(node.path("total").asInt()).isEqualTo(qty);
    }

    private void dispose(Long batchId, String action) throws Exception {
        jsonBody(post("/api/stock/dispose"), "{\"batchId\":" + batchId + ",\"action\":\"" + action
                + "\",\"reason\":\"效期到了，丢弃\"}");
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String jwt) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
    }

    private JsonNode jsonBody(MockHttpServletRequestBuilder request, String body) throws Exception {
        return json(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(withToken(request, token)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String text(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(withToken(request, token)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
    }

    private int code(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("code").asInt();
    }

    private void assertDecimal(JsonNode node, String expected) {
        assertThat(new BigDecimal(node.asText())).isEqualByComparingTo(expected);
    }

    private static String ym(YearMonth month) {
        return String.valueOf(month.getYear() * 100 + month.getMonthValue());
    }
}

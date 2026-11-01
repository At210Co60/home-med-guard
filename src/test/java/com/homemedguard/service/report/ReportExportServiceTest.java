package com.homemedguard.service.report;

import com.homemedguard.service.report.ReportExportService.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 导出的转义与口径计算：这几处错了，报表数字或 Excel 里的内容就会不对。 */
class ReportExportServiceTest {

    private final ReportExportService exporter = new ReportExportService();

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+8613800000000", "-到期", "@SUM(A1)", "\tTAB"})
    @DisplayName("以公式字符开头的用户输入要前置单引号，否则 Excel 会执行它")
    void guardsAgainstCsvFormulaInjection(String hostile) {
        String csv = exporter.toCsv(new Table(List.of("名称"), List.of(List.of((Object) hostile))), null);

        assertThat(csv).contains("'" + hostile);
    }

    @Test
    @DisplayName("逗号与双引号按 CSV 规范加引号转义")
    void escapesCommaAndQuote() {
        String csv = exporter.toCsv(new Table(List.of("名称"),
                List.of(List.of((Object) "阿司匹林, 肠溶\"100mg\""))), null);

        assertThat(csv).contains("\"阿司匹林, 肠溶\"\"100mg\"\"\"");
    }

    @Test
    @DisplayName("带 BOM，中文列在 Excel 里才不乱码；行尾 CRLF")
    void writesBomAndCrlf() {
        String csv = exporter.toCsv(new Table(List.of("月份"), List.of(List.of((Object) "2026-11"))), "水印");

        assertThat(csv).startsWith("﻿");
        assertThat(csv).endsWith("水印\r\n");
        assertThat(csv.lines().count()).isEqualTo(3);
    }

    @Test
    @DisplayName("Markdown 表格里的竖线不能把列撑破")
    void escapesMarkdownPipe() {
        String md = exporter.toMarkdown(new Table(List.of("剂型", "金额"),
                List.of(List.of((Object) "滴眼|液", new BigDecimal("12.50")))), "标题", null);

        assertThat(md).contains("滴眼\\|液");
        assertThat(md).contains("12.50");
        assertThat(md).doesNotContain("null");
    }

    @ParameterizedTest
    @CsvSource({
            "10, 100, 10.00",
            "0, 100, 0.00",
            "33.335, 100, 33.34",
            "5, 0, null",
            "5, '', null"
    })
    @DisplayName("浪费率：分母为 0 或空时返回 null，而不是报 0% 让人误读成零浪费")
    void rateHandlesZeroDenominator(String numerator, String denominator, String expected) {
        BigDecimal n = new BigDecimal(numerator);
        BigDecimal d = denominator.isEmpty() ? null : new BigDecimal(denominator);

        String actual = WasteReportService.rate(n, d) == null
                ? "null" : WasteReportService.rate(n, d).toPlainString();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("跨月区间按真实月份逐格展开，202512 之后是 202601 而不是 202513")
    void monthWalkIsCalendarCorrect() {
        WasteReportService service = new WasteReportService(stubMapper(
                List.of(row(202512, "100"), row(202601, "50")),
                List.of(row(202512, "10"))));

        WasteReportService.WasteSummary summary = service.summarize(1L, 202512, 202602);

        assertThat(summary.months()).extracting(WasteReportService.MonthlyWaste::ym)
                .containsExactly(202512, 202601, 202602);
        assertThat(summary.totalPurchased()).isEqualByComparingTo("150");
        assertThat(summary.totalWasted()).isEqualByComparingTo("10");
        assertThat(summary.totalRatePercent()).isEqualByComparingTo("6.67");
        // 202602 没有购药记录：这一格是 null 而不是 0%，报表上要显示"暂无购药记录"
        assertThat(summary.months().get(2).wasteRatePercent()).isNull();
    }

    @Test
    @DisplayName("非法月份直接拒绝，不返回空表假装成功")
    void rejectsBadMonthFormat() {
        assertThatThrownBy(() -> new WasteReportService(null).summarize(1L, 202613, 202614))
                .hasMessageContaining("yyyyMM");
    }

    private static com.homemedguard.service.report.vo.MonthAmount row(int ym, String amount) {
        com.homemedguard.service.report.vo.MonthAmount row = new com.homemedguard.service.report.vo.MonthAmount();
        row.setYm(ym);
        row.setAmount(new BigDecimal(amount));
        return row;
    }

    private static com.homemedguard.mapper.report.StockReportMapper stubMapper(
            List<com.homemedguard.service.report.vo.MonthAmount> purchased,
            List<com.homemedguard.service.report.vo.MonthAmount> wasted) {
        return new com.homemedguard.mapper.report.StockReportMapper() {
            @Override
            public List<com.homemedguard.service.report.vo.MonthAmount> selectPurchasedMonthly(
                    Long familyId, Integer startYm, Integer endYm) {
                return purchased;
            }

            @Override
            public List<com.homemedguard.service.report.vo.MonthAmount> selectWasteMonthly(
                    Long familyId, Integer startYm, Integer endYm) {
                return wasted;
            }

            @Override
            public List<com.homemedguard.service.report.vo.CategoryExpense> selectExpenseByCategory(
                    Long familyId, Integer startYm, Integer endYm) {
                return List.of();
            }
        };
    }
}

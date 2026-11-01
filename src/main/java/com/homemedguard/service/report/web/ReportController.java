package com.homemedguard.service.report.web;

import com.homemedguard.common.access.FamilyAccessGuard;
import com.homemedguard.common.api.Result;
import com.homemedguard.service.report.ReportExportService;
import com.homemedguard.service.report.ReportExportService.Table;
import com.homemedguard.service.report.ReportModule;
import com.homemedguard.service.report.WasteReportService;
import com.homemedguard.service.report.WasteReportService.ExpenseTrend;
import com.homemedguard.service.report.WasteReportService.WasteSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** 统计报表入口（M6）。区间用 yyyyMM 闭区间，缺省取最近 12 个月。 */
@Tag(name = "07-统计报表")
@ReportModule
@RestController
@Validated
@RequestMapping("/api/report")
public class ReportController {

    private static final String CSV_TYPE = "text/csv;charset=UTF-8";

    private final WasteReportService wasteReport;
    private final ReportExportService exporter;
    private final FamilyAccessGuard access;

    public ReportController(WasteReportService wasteReport, ReportExportService exporter,
                            FamilyAccessGuard access) {
        this.wasteReport = wasteReport;
        this.exporter = exporter;
        this.access = access;
    }

    @Operation(summary = "浪费率：过期丢弃与交回金额 ÷ 同期购药总额，按月")
    @GetMapping("/waste")
    public Result<WasteSummary> waste(@RequestParam(required = false) Long familyId,
                                      @RequestParam(required = false) Integer startYm,
                                      @RequestParam(required = false) Integer endYm) {
        int[] range = range(startYm, endYm);
        return Result.ok(wasteReport.summarize(access.requireFamilyId(familyId), range[0], range[1]));
    }

    @Operation(summary = "支出趋势：月 × 剂型")
    @GetMapping("/expense")
    public Result<ExpenseTrend> expense(@RequestParam(required = false) Long familyId,
                                       @RequestParam(required = false) Integer startYm,
                                       @RequestParam(required = false) Integer endYm) {
        int[] range = range(startYm, endYm);
        return Result.ok(wasteReport.expenseTrend(access.requireFamilyId(familyId), range[0], range[1]));
    }

    @Operation(summary = "浪费率导出 CSV（带 BOM 与导出水印，Excel 打开不乱码）")
    @GetMapping(value = "/waste.csv", produces = CSV_TYPE)
    public ResponseEntity<String> wasteCsv(@RequestParam(required = false) Long familyId,
                                           @RequestParam(required = false) Integer startYm,
                                           @RequestParam(required = false) Integer endYm,
                                           @RequestParam(defaultValue = "unknown") String by) {
        int[] range = range(startYm, endYm);
        WasteSummary summary = wasteReport.summarize(access.requireFamilyId(familyId), range[0], range[1]);
        // 没有购药记录的月份浪费率是 null，用 Arrays.asList 而不是 List.of：后者遇到 null 直接抛
        Table table = new Table(List.of("月份", "购药总额", "浪费金额", "浪费率%"),
                summary.months().stream()
                        .map(m -> Arrays.asList((Object) format(m.ym()), m.purchased(), m.wasted(),
                                m.wasteRatePercent()))
                        .toList());
        String csv = exporter.toCsv(table, exporter.watermark(by));
        return download(csv, "waste-" + range[0] + "-" + range[1] + ".csv",
                MediaType.parseMediaType(CSV_TYPE));
    }

    @Operation(summary = "支出趋势导出 Markdown，便于贴进答辩材料")
    @GetMapping(value = "/expense.md", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> expenseMarkdown(@RequestParam(required = false) Long familyId,
                                                 @RequestParam(required = false) Integer startYm,
                                                 @RequestParam(required = false) Integer endYm,
                                                 @RequestParam(defaultValue = "unknown") String by) {
        int[] range = range(startYm, endYm);
        ExpenseTrend trend = wasteReport.expenseTrend(access.requireFamilyId(familyId), range[0], range[1]);
        Table table = new Table(List.of("月份", "剂型", "金额", "批次数"),
                trend.rows().stream()
                        .map(r -> Arrays.asList((Object) format(r.getYm()), r.getCategory(), r.getAmount(),
                                r.getBatches()))
                        .toList());
        return download(exporter.toMarkdown(table, "药箱支出趋势", exporter.watermark(by)),
                "expense-" + range[0] + "-" + range[1] + ".md",
                MediaType.parseMediaType("text/markdown;charset=UTF-8"));
    }

    private static ResponseEntity<String> download(String body, String filename, MediaType mediaType) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    private static int[] range(Integer startYm, Integer endYm) {
        int current = LocalDate.now().getYear() * 100 + LocalDate.now().getMonthValue();
        int end = endYm == null ? current : endYm;
        int start = startYm == null ? monthsBack(end, 11) : startYm;
        return new int[]{start, end};
    }

    private static int monthsBack(int ym, int back) {
        int index = (ym / 100) * 12 + (ym % 100) - 1 - back;
        return (index / 12) * 100 + (index % 12) + 1;
    }

    private static String format(int ym) {
        return String.format("%04d-%02d", ym / 100, ym % 100);
    }
}

package com.homemedguard.service.report;

import com.homemedguard.service.rule.api.RiskEvaluateFacade;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报表导出（handoff §8）。没引 EasyExcel/POI，先出 CSV 与 Markdown 两种文本格式，
 * 加依赖要动 pom.xml（守门人文件），需要 A 点头后再换 EasyExcel。
 */
@ReportModule
public class ReportExportService {

    public record Table(List<String> header, List<List<Object>> rows) {
    }

    /** 导出水印 + 免责口径，健康类数据出表必须带（handoff §10）。 */
    public String watermark(String generatedBy) {
        return "导出人：" + generatedBy + "；口径按购入价；" + RiskEvaluateFacade.DISCLAIMER;
    }

    /**
     * BOM 是给 Excel 的：不带的话中文列在 Excel 里直接变乱码，家庭用户第一个撞到的就是这个。
     * 行分隔用 CRLF，兼容 Windows 记事本与 Excel。
     */
    public String toCsv(Table table, String watermark) {
        StringBuilder sb = new StringBuilder("﻿");
        appendRow(sb, table.header());
        for (List<Object> row : table.rows()) {
            appendRow(sb, row);
        }
        if (watermark != null && !watermark.isBlank()) {
            sb.append(cell(watermark)).append("\r\n");
        }
        return sb.toString();
    }

    public String toMarkdown(Table table, String title, String watermark) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("| ").append(String.join(" | ", table.header())).append(" |\n");
        sb.append("|").append(" --- |".repeat(table.header().size())).append("\n");
        for (List<Object> row : table.rows()) {
            sb.append("| ");
            for (int i = 0; i < row.size(); i++) {
                sb.append(mdCell(row.get(i)));
                sb.append(i == row.size() - 1 ? " |\n" : " | ");
            }
        }
        if (watermark != null && !watermark.isBlank()) {
            sb.append("\n> ").append(watermark).append("\n");
        }
        return sb.toString();
    }

    /**
     * CSV 单元格。除了常规转义，还把以 = + - @ 制表符 开头的值前置单引号：
     * 药品名是用户输入，不加这层就会在 Excel 里被当公式执行（CSV 注入，OWASP 里算 XSS 同类）。
     */
    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String raw = value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
        if (!raw.isEmpty()) {
            char first = raw.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                raw = "'" + raw;
            }
        }
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n") || raw.contains("\r")) {
            return '"' + raw.replace("\"", "\"\"") + '"';
        }
        return raw;
    }

    private static String mdCell(Object value) {
        if (value == null) {
            return "";
        }
        String raw = value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
        return raw.replace("|", "\\|").replace("\n", " ");
    }

    private void appendRow(StringBuilder sb, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            sb.append(cell(cells.get(i)));
            sb.append(i == cells.size() - 1 ? "\r\n" : ",");
        }
    }
}

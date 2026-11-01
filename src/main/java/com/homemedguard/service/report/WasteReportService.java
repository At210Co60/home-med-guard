package com.homemedguard.service.report;

import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.error.BizException;
import com.homemedguard.mapper.report.StockReportMapper;
import com.homemedguard.service.report.vo.CategoryExpense;
import com.homemedguard.service.report.vo.MonthAmount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 浪费率与支出趋势（handoff §8）。口径固定为"按购入价"，
 * 分母是同期购药总额；分母为 0 时不出百分比而不是报 0%，那会把"还没开始记账"误读成"零浪费"。
 */
@ReportModule
public class WasteReportService {

    /** 浪费只认 DISPOSED / RETURNED，与 SQL 里的 IN 列表保持一致。 */
    public static final String CALIBER = "浪费率 = 过期丢弃与交回的金额 ÷ 同期购药总额，均按购入价计算";

    public record MonthlyWaste(int ym, BigDecimal purchased, BigDecimal wasted, BigDecimal wasteRatePercent) {
    }

    public record WasteSummary(Long familyId, int startYm, int endYm,
                               BigDecimal totalPurchased, BigDecimal totalWasted,
                               BigDecimal totalRatePercent, List<MonthlyWaste> months, String caliber) {
    }

    public record ExpenseTrend(Long familyId, int startYm, int endYm, List<CategoryExpense> rows) {
    }

    private final StockReportMapper reportMapper;

    public WasteReportService(StockReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    public WasteSummary summarize(Long familyId, int startYm, int endYm) {
        requireRange(familyId, startYm, endYm);
        Map<Integer, BigDecimal> purchased = index(reportMapper.selectPurchasedMonthly(familyId, startYm, endYm));
        Map<Integer, BigDecimal> wasted = index(reportMapper.selectWasteMonthly(familyId, startYm, endYm));

        List<MonthlyWaste> months = new ArrayList<>();
        BigDecimal totalPurchased = BigDecimal.ZERO;
        BigDecimal totalWasted = BigDecimal.ZERO;
        int year = startYm / 100;
        int month = startYm % 100;
        int lastYm = endYm;
        while (year * 100 + month <= lastYm) {
            int ym = year * 100 + month;
            BigDecimal p = purchased.getOrDefault(ym, BigDecimal.ZERO);
            BigDecimal w = wasted.getOrDefault(ym, BigDecimal.ZERO);
            totalPurchased = totalPurchased.add(p);
            totalWasted = totalWasted.add(w);
            months.add(new MonthlyWaste(ym, p, w, rate(w, p)));
            month++;
            if (month > 12) {
                month = 1;
                year++;
            }
        }
        return new WasteSummary(familyId, startYm, endYm, totalPurchased, totalWasted,
                rate(totalWasted, totalPurchased), months, CALIBER);
    }

    public ExpenseTrend expenseTrend(Long familyId, int startYm, int endYm) {
        requireRange(familyId, startYm, endYm);
        return new ExpenseTrend(familyId, startYm, endYm,
                reportMapper.selectExpenseByCategory(familyId, startYm, endYm));
    }

    /** 分母为 0 返回 null，由前端显示"暂无购药记录"。 */
    static BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private static void requireRange(Long familyId, int startYm, int endYm) {
        if (familyId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "familyId 不能为空");
        }
        if (!isPlausibleMonth(startYm) || !isPlausibleMonth(endYm)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月份格式应为 yyyyMM，例如 202611");
        }
        if (startYm > endYm) {
            throw new BizException(ErrorCode.PARAM_INVALID, "起始月不能晚于结束月");
        }
        // 一次最多取 24 个月，避免报表把整张表扫一遍
        if (monthIndex(endYm) - monthIndex(startYm) + 1 > 24) {
            throw new BizException(ErrorCode.PARAM_INVALID, "报表区间最长 24 个月");
        }
    }

    private static boolean isPlausibleMonth(int ym) {
        int month = ym % 100;
        return ym / 100 >= 1970 && ym / 100 <= 2999 && month >= 1 && month <= 12;
    }

    private static int monthIndex(int ym) {
        return (ym / 100) * 12 + (ym % 100);
    }

    private static Map<Integer, BigDecimal> index(List<MonthAmount> rows) {
        Map<Integer, BigDecimal> map = new LinkedHashMap<>();
        for (MonthAmount row : rows) {
            if (row.getYm() != null) {
                map.merge(row.getYm(), row.getAmount() == null ? BigDecimal.ZERO : row.getAmount(), BigDecimal::add);
            }
        }
        return map;
    }
}

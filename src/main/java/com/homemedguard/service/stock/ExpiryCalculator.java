package com.homemedguard.service.stock;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 效期计算统一入口（handoff §2「效期计算统一入口」）。
 * 时间一律在服务端算，时区固定 Asia/Shanghai，前端只展示（§四.6）。
 */
@Component
public class ExpiryCalculator {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * 双轨取严：{@code deadline = min(有效期, 开封日期 + 开封后天数)}。
     * 未开封、或剂型没有开封后规则时退回有效期——宁可不预警，也不编造一个期限。
     */
    public LocalDate useByDate(LocalDate expiryDate, LocalDate openedDate, Integer openUseDays) {
        if (openedDate == null || openUseDays == null || openUseDays <= 0) {
            return expiryDate;
        }
        LocalDate openedLimit = openedDate.plusDays(openUseDays);
        if (expiryDate == null) {
            return openedLimit;
        }
        return openedLimit.isBefore(expiryDate) ? openedLimit : expiryDate;
    }

    /** 距期限还有几天；负数表示已超期。deadline 为空返回 Long.MAX_VALUE，调用方不预警。 */
    public long daysLeft(LocalDate today, LocalDate deadline) {
        return deadline == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(today, deadline);
    }
}

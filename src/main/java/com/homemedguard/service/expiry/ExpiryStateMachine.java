package com.homemedguard.service.expiry;

import com.homemedguard.service.stock.api.StockQueryFacade.BatchView;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 效期状态机（handoff §3）：EXPIRING_NEAR -> EXPIRED -> DISPOSED/RENEWED/HANDLED。
 * DISPOSED/RENEWED/HANDLED 由处置动作决定，不在这里判；本类只回答
 * "今天这支批次算什么档、下次什么时候再来看它"。纯函数，不碰库。
 */
@ExpiryModule
public class ExpiryStateMachine {

    public static final String TASK_EXPIRY_NEAR = "EXPIRY_NEAR";
    public static final String TASK_EXPIRED = "EXPIRED";
    public static final String TASK_OPENED_OVERDUE = "OPENED_OVERDUE";

    public static final String LEVEL_LOW = "LOW";
    public static final String LEVEL_MID = "MID";
    public static final String LEVEL_HIGH = "HIGH";

    /** 三档临期阈值（天），来自 sys_config，家庭级可覆盖。 */
    public record Thresholds(int advisoryDays, int warningDays, int urgentDays) {

        /** 防止管理员把三档填成乱序或负数，让同一批次一天里连响三次。 */
        public Thresholds normalized() {
            int a = Math.max(0, advisoryDays);
            int w = Math.max(0, warningDays);
            int u = Math.max(0, urgentDays);
            int low = Math.min(u, Math.min(w, a));
            int high = Math.max(u, Math.max(w, a));
            int mid = u + w + a - low - high;
            return new Thresholds(high, mid, low);
        }
    }

    /**
     * @param batchStatus   回写给库存的批次状态
     * @param taskType      需要生成的任务类型，null 表示本次不出任务
     * @param nextCheckDate 下次巡检日，null 表示不再自动巡检（等人工处置）
     */
    public record Verdict(String batchStatus, String taskType, String level, LocalDate nextCheckDate) {
    }

    private static final String STATUS_NORMAL = "NORMAL";
    private static final String STATUS_EXPIRING_NEAR = "EXPIRING_NEAR";
    private static final String STATUS_EXPIRED = "EXPIRED";

    public Verdict evaluate(LocalDate today, BatchView batch, Thresholds thresholds) {
        if (batch == null || batch.useByDate() == null) {
            // 双轨都算不出期限：不预警，也不把批次踢出巡检
            return new Verdict(STATUS_NORMAL, null, null, null);
        }
        Thresholds t = thresholds.normalized();
        long daysLeft = ChronoUnit.DAYS.between(today, batch.useByDate());

        if (daysLeft < 0) {
            boolean boundByOpening = batch.expiryDate() != null
                    && batch.useByDate().isBefore(batch.expiryDate());
            return new Verdict(STATUS_EXPIRED,
                    boundByOpening ? TASK_OPENED_OVERDUE : TASK_EXPIRED,
                    LEVEL_HIGH, null);
        }
        if (daysLeft <= t.urgentDays()) {
            return new Verdict(STATUS_EXPIRING_NEAR, TASK_EXPIRY_NEAR, LEVEL_HIGH, batch.useByDate());
        }
        if (daysLeft <= t.warningDays()) {
            return new Verdict(STATUS_EXPIRING_NEAR, TASK_EXPIRY_NEAR, LEVEL_MID,
                    batch.useByDate().minusDays(t.urgentDays()));
        }
        if (daysLeft <= t.advisoryDays()) {
            return new Verdict(STATUS_EXPIRING_NEAR, TASK_EXPIRY_NEAR, LEVEL_LOW,
                    batch.useByDate().minusDays(t.warningDays()));
        }
        return new Verdict(STATUS_NORMAL, null, null,
                batch.useByDate().minusDays(t.advisoryDays()));
    }
}

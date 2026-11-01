package com.homemedguard.service.stock;

import java.util.Set;

/**
 * 批次状态位。DB 里存字符串（对齐 sys_config 等表的做法），不引入 MyBatis-Plus 枚举处理器。
 * 状态机走向见 handoff §3：EXPIRING_NEAR -> EXPIRED -> DISPOSED/RENEWED/HANDLED，
 * DEPLETED 是数量扣光的终态。
 */
public final class BatchStatus {

    public static final String NORMAL = "NORMAL";
    public static final String EXPIRING_NEAR = "EXPIRING_NEAR";
    public static final String EXPIRED = "EXPIRED";
    public static final String DISPOSED = "DISPOSED";
    public static final String RENEWED = "RENEWED";
    public static final String HANDLED = "HANDLED";
    public static final String DEPLETED = "DEPLETED";

    /** 进了终态就不再参与 FEFO 消耗，也不再被每日扫描巡检。 */
    private static final Set<String> TERMINAL = Set.of(DISPOSED, RENEWED, HANDLED, DEPLETED);

    private BatchStatus() {
    }

    public static boolean isTerminal(String status) {
        return status != null && TERMINAL.contains(status);
    }
}

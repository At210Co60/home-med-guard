package com.homemedguard.service.stock.api;

import java.time.LocalDate;

/**
 * 效期结论回写库存的唯一入口。expiry 模块不直接 UPDATE stock_batch——
 * 按 §6 铁律，跨模块状态变化只走接口，写权限仍然只在 stock 自己手里。
 */
public interface StockMaintenanceFacade {

    /**
     * @param status        批次新状态
     * @param nextActionDate 下次该被扫描的日子，null 表示不再自动巡检
     */
    void applyExpiryVerdict(Long batchId, String status, LocalDate nextActionDate);
}

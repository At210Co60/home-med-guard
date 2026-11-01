package com.homemedguard.service.stock.api;

import java.time.LocalDate;

/**
 * 批次处置完成事件。按 CONTRIBUTING §6，跨模块状态变化走领域事件而不是直连：
 * stock 发布，expiry 监听后把该批次的待处理任务收尾，两边互不注入 Mapper。
 */
public record BatchDisposedEvent(Long batchId,
                                 Long familyId,
                                 String action,
                                 LocalDate bizDate,
                                 java.math.BigDecimal wastedAmount) {
}

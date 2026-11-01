package com.homemedguard.service.expiry;

import com.homemedguard.service.stock.api.BatchDisposedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

/**
 * 处置完成 → 关任务。用 ApplicationEventPublisher 而不是让 stock 直接改 action_task，
 * 这是 CONTRIBUTING §6 "跨模块状态变化用事件解耦" 的落点：删掉本类，库存与处置照常工作。
 */
@ExpiryModule
public class BatchDisposedListener {

    private static final Logger log = LoggerFactory.getLogger(BatchDisposedListener.class);

    private final ActionTaskService taskService;

    public BatchDisposedListener(ActionTaskService taskService) {
        this.taskService = taskService;
    }

    @EventListener
    public void onBatchDisposed(BatchDisposedEvent event) {
        int closed = taskService.closeOpenTasks(event.batchId(), event.bizDate(), null);
        if (closed > 0) {
            log.info("批次 {} 已处置（{}），顺带关闭 {} 条待处理任务", event.batchId(), event.action(), closed);
        }
    }
}

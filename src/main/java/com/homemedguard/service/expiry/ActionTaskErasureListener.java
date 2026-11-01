package com.homemedguard.service.expiry;

import com.homemedguard.common.contract.AccountErasureRequestedEvent;
import com.homemedguard.service.expiry.mapper.ActionTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

/** 账号注销时清效期任务：标题里带着药名与效期，属于跟家庭走的健康相关数据。 */
@ExpiryModule
public class ActionTaskErasureListener {

    private static final Logger log = LoggerFactory.getLogger(ActionTaskErasureListener.class);

    private final ActionTaskMapper tasks;

    public ActionTaskErasureListener(ActionTaskMapper tasks) {
        this.tasks = tasks;
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onAccountErasure(AccountErasureRequestedEvent event) {
        if (!event.familyDeleted() || event.familyId() == null) {
            return;
        }
        int rows = tasks.eraseByFamily(event.familyId());
        if (rows > 0) {
            log.info("家庭 {} 注销，效期侧清除待处理任务 {} 条", event.familyId(), rows);
        }
    }
}

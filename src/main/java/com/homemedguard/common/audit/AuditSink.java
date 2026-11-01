package com.homemedguard.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 审计是旁路：写失败只记 error，绝不能把业务请求带崩。 */
@Component
public class AuditSink {

    private static final Logger log = LoggerFactory.getLogger(AuditSink.class);

    private final AuditLogMapper auditLogMapper;

    public AuditSink(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Async("auditExecutor")
    public void write(AuditLogEntity row) {
        try {
            auditLogMapper.insert(row);
        } catch (RuntimeException ex) {
            log.error("审计日志写入失败 action={} cause={}", row.getAction(), ex.getMessage());
        }
    }
}

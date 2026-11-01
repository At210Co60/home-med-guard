package com.homemedguard.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在写接口上，异步落 audit_log。
 * before_json 记的是入参快照、after_json 记的是返回值快照：
 * 真正的"改前/改后"要业务层显式读旧值，代价太高，这里先保证"谁在什么时候动了什么"可追。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    String action();

    String targetType();
}

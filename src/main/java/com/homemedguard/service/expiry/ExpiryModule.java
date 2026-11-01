package com.homemedguard.service.expiry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 效期状态机与每日扫描模块的 Bean 标记（app.module.expiry）。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
@ConditionalOnProperty(prefix = "app.module", name = "expiry", havingValue = "true", matchIfMissing = true)
public @interface ExpiryModule {
}

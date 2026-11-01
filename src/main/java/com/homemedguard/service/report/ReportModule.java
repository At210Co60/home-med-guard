package com.homemedguard.service.report;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 统计报表模块的 Bean 标记（app.module.report）。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
@ConditionalOnProperty(prefix = "app.module", name = "report", havingValue = "true", matchIfMissing = true)
public @interface ReportModule {
}

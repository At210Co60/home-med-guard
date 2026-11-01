package com.homemedguard.service.stock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 批次库存模块的 Bean 标记（app.module.stock）。关掉后整块不注册，
 * 系统照常启动、其他模块不受影响——与 service/rule 的 RuleModule 同一套证据。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
@ConditionalOnProperty(prefix = "app.module", name = "stock", havingValue = "true", matchIfMissing = true)
public @interface StockModule {
}

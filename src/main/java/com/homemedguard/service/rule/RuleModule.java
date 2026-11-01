package com.homemedguard.service.rule;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ② 规则引擎模块的 Bean 标记。关掉 app.module.rule 时整块消失，
 * 系统照常启动、①③ 功能不受影响（CONTRIBUTING §6.8 的可验证证据）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
@ConditionalOnProperty(prefix = "app.module", name = "rule", havingValue = "true", matchIfMissing = true)
public @interface RuleModule {
}

package com.homemedguard.service.family;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** ① 家庭与档案模块的 Bean 标记，开关 app.module.family。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
@ConditionalOnProperty(prefix = "app.module", name = "family", havingValue = "true", matchIfMissing = true)
public @interface FamilyModule {
}

package com.homemedguard.service.drug;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** ① 药品字典模块的 Bean 标记，开关 app.module.drug。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
@ConditionalOnProperty(prefix = "app.module", name = "drug", havingValue = "true", matchIfMissing = true)
public @interface DrugModule {
}

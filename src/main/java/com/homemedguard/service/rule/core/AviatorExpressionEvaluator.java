package com.homemedguard.service.rule.core;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 规则表达式求值。表达式只来自审核发布后的 rule 表，不接受任何前端传入的字符串。
 * 求值异常返回 empty，由调用方记进 failedRuleCodes——不能把"算不出来"当成"没风险"。
 */
@Component
public class AviatorExpressionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AviatorExpressionEvaluator.class);

    private final AviatorEvaluatorInstance engine = AviatorEvaluator.getInstance();

    public static boolean hasExpression(String expr) {
        return expr != null && !expr.isBlank();
    }

    /** 发布前语法体检：写错的表达式不允许进规则库，进了也只会被记成"求值失败"。 */
    public boolean validates(String expr) {
        if (!hasExpression(expr)) {
            return true;
        }
        try {
            engine.compile(expr, false);
            return true;
        } catch (RuntimeException ex) {
            log.warn("规则表达式编译失败 expr={} cause={}", expr, ex.getMessage());
            return false;
        }
    }

    public Optional<Boolean> test(String expr, Map<String, Object> env) {
        try {
            Object value = engine.execute(expr, env, true);
            if (value instanceof Boolean bool) {
                return Optional.of(bool);
            }
            log.warn("规则表达式结果不是布尔 expr={} value={}", expr, value);
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("规则表达式求值失败 expr={} cause={}", expr, ex.getMessage());
            return Optional.empty();
        }
    }
}

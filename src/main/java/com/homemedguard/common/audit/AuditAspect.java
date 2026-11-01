package com.homemedguard.common.audit;

import com.homemedguard.service.auth.core.LoginUser;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 写接口审计。参数与返回值都过一遍 {@link AuditPayloadSanitizer}，
 * 健康原文和口令一律不进审计表。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditSink sink;
    private final ObjectMapper objectMapper;

    public AuditAspect(AuditSink sink, ObjectMapper objectMapper) {
        this.sink = sink;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable error) {
            record(joinPoint, auditLog, null, describe(error));
            throw error;
        }
        record(joinPoint, auditLog, result, null);
        return result;
    }

    private void record(ProceedingJoinPoint joinPoint, AuditLog auditLog, Object result, String error) {
        try {
            AuditLogEntity row = new AuditLogEntity();
            row.setAction(auditLog.action());
            row.setTargetType(auditLog.targetType());
            row.setTargetId(firstId(joinPoint.getArgs()));
            row.setBeforeJson(AuditPayloadSanitizer.toJson(objectMapper, auditableArgs(joinPoint.getArgs())));
            row.setAfterJson(error != null ? "{\"error\":\"" + error + "\"}"
                    : AuditPayloadSanitizer.toJson(objectMapper, result));
            currentPrincipal().ifPresent(user -> row.setOperatorUserId(user.userId()));
            // familyId 不从请求头取（可伪造），需要按家庭维度审计的接口自己在返回值里带上
            currentRequest().ifPresent(request -> row.setIp(request.getRemoteAddr()));
            sink.write(row);
        } catch (RuntimeException ex) {
            log.warn("审计记录组装失败 action={}", auditLog.action(), ex);
        }
    }

    private static List<Object> auditableArgs(Object[] args) {
        return Arrays.stream(args)
                .filter(arg -> !(arg instanceof ServletRequest) && !(arg instanceof ServletResponse))
                .toList();
    }

    private static String firstId(Object[] args) {
        return Arrays.stream(args)
                .filter(arg -> arg instanceof Number || arg instanceof String)
                .map(String::valueOf)
                .findFirst()
                .orElse(null);
    }

    private static Optional<LoginUser> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    private static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }

    private static String describe(Throwable error) {
        return error.getClass().getSimpleName();
    }
}

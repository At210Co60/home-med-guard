package com.homemedguard.common.api;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。前两位对齐 HTTP 语义，后三位细分；风险拦截是专用码，前端拦截器据此弹"仍需提交"确认框。
 */
public enum ErrorCode {

    SUCCESS(0, "OK", HttpStatus.OK),
    PARAM_INVALID(40001, "参数不合法", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "没有该操作权限", HttpStatus.FORBIDDEN),
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    IDEMPOTENT_CONFLICT(40900, "重复请求，已按首次结果处理", HttpStatus.CONFLICT),
    RISK_CONFIRM_REQUIRED(42201, "命中高危用药冲突，需二次确认后才能继续", HttpStatus.UNPROCESSABLE_ENTITY),
    RULE_VERSION_STALE(42202, "规则版本已过期，请重新评估", HttpStatus.UNPROCESSABLE_ENTITY),
    DEPENDENCY_UNAVAILABLE(50300, "依赖模块未启用，本次结果不完整", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(50000, "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}

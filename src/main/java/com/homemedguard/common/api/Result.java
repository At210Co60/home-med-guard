package com.homemedguard.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.homemedguard.common.trace.TraceId;

import java.util.List;

/**
 * 统一返回体。业务错误一律 HTTP 状态由 {@link ErrorCode#httpStatus()} 决定，body 结构恒定。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Result<T>(int code, String message, T data, String traceId, List<FieldError> fieldErrors) {

    public record FieldError(String field, String reason) {
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.message(), data, TraceId.current(), null);
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.code(), errorCode.message(), null, TraceId.current(), null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.code(), message, null, TraceId.current(), null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message, List<FieldError> fieldErrors) {
        return new Result<>(errorCode.code(), message, null, TraceId.current(), fieldErrors);
    }
}

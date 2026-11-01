package com.homemedguard.common.error;

import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.api.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex) {
        ErrorCode code = ex.errorCode();
        if (code.httpStatus().is5xxServerError()) {
            log.error("业务异常 code={} msg={}", code.code(), ex.getMessage(), ex);
        } else {
            log.warn("业务拒绝 code={} msg={}", code.code(), ex.getMessage());
        }
        return ResponseEntity.status(code.httpStatus()).body(Result.fail(code, ex.getMessage()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException ex) {
        List<Result.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new Result.FieldError(err.getField(), err.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.httpStatus())
                .body(Result.fail(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.message(), fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleViolation(ConstraintViolationException ex) {
        List<Result.FieldError> fields = ex.getConstraintViolations().stream()
                .map(v -> new Result.FieldError(String.valueOf(v.getPropertyPath()), v.getMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.httpStatus())
                .body(Result.fail(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.message(), fields));
    }

    /** @PreAuthorize 拒绝必须给 403，不能被下面的兜底 handler 报成 500。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("越权访问被拒：{}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.httpStatus()).body(Result.fail(ErrorCode.FORBIDDEN));
    }

    /** 请求体不是合法 JSON 属于客户端错误，不能算系统内部错误。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败：{}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.httpStatus())
                .body(Result.fail(ErrorCode.PARAM_INVALID, "请求体不是合法 JSON，注意 UTF-8 编码"));
    }

    /**
     * 路径没注册（比如模块开关关掉后接口下线）必须是 404，不能被兜底 handler 报成 500。
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Result<Void>> handleNoHandler(Exception ex) {
        log.warn("接口不存在：{}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.httpStatus()).body(Result.fail(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception ex) {
        log.error("未预期异常 {}", ex.getMessage(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(Result.fail(ErrorCode.INTERNAL_ERROR));
    }
}

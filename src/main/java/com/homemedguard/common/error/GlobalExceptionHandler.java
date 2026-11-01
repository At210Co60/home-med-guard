package com.homemedguard.common.error;

import com.homemedguard.common.api.ErrorCode;
import com.homemedguard.common.api.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception ex) {
        log.error("未预期异常 {}", ex.getMessage(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(Result.fail(ErrorCode.INTERNAL_ERROR));
    }
}

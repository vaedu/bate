package com.beat.pay.common;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 全局异常处理：统一输出 Result 包装 + 对应 HTTP 状态码 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Object>> handleBiz(BizException e) {
        return build(e.getErrorCode(), e.getMessage(), e.getData());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleBodyInvalid(MethodArgumentNotValidException e) {
        return build(ErrorCode.INVALID_PARAM, fieldErrors(e), null);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Object>> handleBind(BindException e) {
        return build(ErrorCode.INVALID_PARAM, fieldErrors(e), null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Object>> handleConstraint(ConstraintViolationException e) {
        return build(ErrorCode.INVALID_PARAM, e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return build(ErrorCode.INVALID_PARAM, "参数类型错误：" + e.getName(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Object>> handleNotReadable(HttpMessageNotReadableException e) {
        return build(ErrorCode.MALFORMED_BODY, null, null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Object>> handleMissingHeader(MissingRequestHeaderException e) {
        if (Constants.HEADER_IDEMPOTENCY_KEY.equalsIgnoreCase(e.getHeaderName())) {
            return build(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, null, null);
        }
        return build(ErrorCode.INVALID_PARAM, "缺少请求头：" + e.getHeaderName(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleOther(Exception e) {
        log.error("Unhandled exception", e);
        return build(ErrorCode.INTERNAL_ERROR, null, null);
    }

    private static String fieldErrors(BindException e) {
        return e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
    }

    private static ResponseEntity<Result<Object>> build(ErrorCode code, String message, Object data) {
        return ResponseEntity.status(code.getHttpStatus()).body(Result.fail(code, message, data));
    }
}

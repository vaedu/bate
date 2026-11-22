package com.beat.pay.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

/** 统一响应：{ code, message, data, requestId, timestamp }，code=0 表示成功 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String requestId;
    private long timestamp;

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.OK.getCode(), ErrorCode.OK.getMessage(), data,
                MDC.get(Constants.MDC_REQUEST_ID), System.currentTimeMillis());
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message, T data) {
        return new Result<>(errorCode.getCode(), message != null ? message : errorCode.getMessage(), data,
                MDC.get(Constants.MDC_REQUEST_ID), System.currentTimeMillis());
    }
}

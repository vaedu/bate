package com.beat.pay.common;

import lombok.Getter;

/** 业务错误码（与接口文档第 7 章一一对应），httpStatus 为建议的 HTTP 状态码 */
@Getter
public enum ErrorCode {

    OK(0, 200, "success"),
    INVALID_PARAM(10001, 400, "参数错误"),
    MALFORMED_BODY(10002, 400, "请求体不是合法JSON"),
    IDEMPOTENCY_KEY_REQUIRED(10003, 400, "缺少 Idempotency-Key"),
    IDEMPOTENCY_KEY_REUSED(10004, 422, "同一幂等键对应不同请求内容"),
    PAYLOAD_TOO_LARGE(10005, 413, "请求条数超限"),
    UNAUTHENTICATED(20001, 401, "未认证"),
    ACCESS_TOKEN_EXPIRED(20002, 401, "Access Token 已过期"),
    TOKEN_INVALID(20003, 401, "Token 无效"),
    REFRESH_TOKEN_INVALID(20004, 401, "Refresh Token 无效或过期"),
    REFRESH_TOKEN_REPLAYED(20005, 401, "检测到 Refresh Token 重放，会话已吊销"),
    HUAWEI_AUTH_FAILED(20006, 401, "华为授权校验失败"),
    SESSION_REVOKED(20007, 401, "当前设备会话已被撤销"),
    ACCOUNT_RESTRICTED(20008, 403, "账号已冻结或处于注销冷静期"),
    FORBIDDEN(30001, 403, "无权限"),
    NOT_FOUND(30002, 404, "资源不存在"),
    VERSION_CONFLICT(40001, 409, "版本冲突"),
    CURSOR_INVALID(40002, 409, "同步游标无效"),
    MIGRATION_STATE_INVALID(40003, 409, "迁移任务状态非法"),
    MIGRATION_VERIFY_FAILED(40004, 422, "迁移校验不通过"),
    MIGRATION_NOT_FOUND(40005, 404, "迁移任务不存在"),
    TIMER_ALREADY_RUNNING(50001, 409, "已有正在运行的计时会话"),
    TIMER_STATE_INVALID(50002, 409, "当前状态不允许该操作"),
    TIMER_NOT_FOUND(50003, 404, "计时会话不存在"),
    TIMER_TIME_INVALID(50004, 422, "时间戳不合法"),
    AMOUNT_OUT_OF_RANGE(50005, 422, "金额超出合理区间"),
    TIMER_NOT_OWNER_DEVICE(50006, 403, "仅会话所属设备可暂停/继续"),
    RATE_LIMITED(60001, 429, "请求过于频繁"),
    INTERNAL_ERROR(90001, 500, "服务内部错误"),
    SERVICE_UNAVAILABLE(90002, 503, "服务暂不可用");

    private final int code;
    private final int httpStatus;
    private final String message;

    ErrorCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}

package com.beat.pay.common;

public final class Constants {
    private Constants() {}

    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String PROVIDER_HUAWEI = "HUAWEI";
    /** 设置实体的固定同步 entityId */
    public static final String SETTINGS_ENTITY_ID = "settings";
}

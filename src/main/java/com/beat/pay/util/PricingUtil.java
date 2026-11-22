package com.beat.pay.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 计价工具（需求 5.5）：片段金额 = round_half_up(durationMs × rateCentsPerHour ÷ 3,600,000) 分 */
public final class PricingUtil {
    private PricingUtil() {}

    private static final BigDecimal MS_PER_HOUR = BigDecimal.valueOf(3_600_000L);

    public static long amountCents(long durationMs, int rateCentsPerHour) {
        return BigDecimal.valueOf(durationMs)
                .multiply(BigDecimal.valueOf(rateCentsPerHour))
                .divide(MS_PER_HOUR, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}

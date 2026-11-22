package com.beat.pay.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 需求 5.3 示例：基础时薪 30 元，阈值 8h，已工作 6h，本次 4h -> 前 2h×30 + 后 2h×45 = 150 元 */
class PricingUtilTest {

    private static final long HOUR = 3_600_000L;

    @Test
    void overtimeExampleFromSpec() {
        long normal = PricingUtil.amountCents(2 * HOUR, 3000);
        long overtime = PricingUtil.amountCents(2 * HOUR, 4500);
        assertEquals(6000, normal);
        assertEquals(9000, overtime);
        assertEquals(15000, normal + overtime);
    }

    @Test
    void roundsHalfUpToCent() {
        // 1 秒 × 3000 分/小时 = 0.8333 分 -> 1 分；0.5 分向上取整
        assertEquals(1, PricingUtil.amountCents(1000, 3000));
        assertEquals(1, PricingUtil.amountCents(600, 3000));   // 0.5
        assertEquals(0, PricingUtil.amountCents(599, 3000));   // 0.4992
    }
}

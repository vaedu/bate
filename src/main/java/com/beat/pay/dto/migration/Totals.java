package com.beat.pay.dto.migration;

import lombok.Data;

/** 迁移校验值 */
@Data
public class Totals {
    /** 记录总时长（毫秒） */
    private Long totalDurationMs;

    /** 记录总金额（分） */
    private Long totalEarnCents;
}

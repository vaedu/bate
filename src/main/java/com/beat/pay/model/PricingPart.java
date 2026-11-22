package com.beat.pay.model;

import com.beat.pay.enums.RateType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 分段内的计价片段 */
@Data
public class PricingPart {
    /** 计价类型 */
    @NotNull
    private RateType rateType;

    /** 片段时长（毫秒） */
    @NotNull
    private Long durationMs;

    /** 费率（分/小时） */
    @NotNull
    private Integer rateCentsPerHour;

    /** 片段金额（分） */
    @NotNull
    private Long amountCents;
}

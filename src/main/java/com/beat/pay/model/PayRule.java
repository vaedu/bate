package com.beat.pay.model;

import com.beat.pay.enums.PayMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 计薪规则（设置与记录快照共用） */
@Data
public class PayRule {
    /** 计薪模式 */
    @NotNull
    private PayMode payMode;

    /** 基础时薪，分/小时 */
    @NotNull
    @Min(0)
    private Integer baseRateCents;

    /** 每日加班阈值（分钟），默认480 */
    @NotNull
    @Min(0)
    private Integer overtimeThresholdMinutes;

    /** 自定义费率；法定模式忽略 */
    @Valid
    private CustomRates customRates;
}

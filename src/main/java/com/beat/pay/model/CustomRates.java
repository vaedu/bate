package com.beat.pay.model;

import jakarta.validation.Valid;
import lombok.Data;

/** 自定义模式三档费率 */
@Data
public class CustomRates {
    /** 加班 */
    @Valid
    private RateInput overtime;

    /** 休息日 */
    @Valid
    private RateInput restDay;

    /** 节假日 */
    @Valid
    private RateInput holiday;
}

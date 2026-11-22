package com.beat.pay.model;

import com.beat.pay.enums.RateInputType;
import lombok.Data;

/** 自定义费率输入 */
@Data
public class RateInput {
    /** 用户原始输入，如 1.5X / 37.5 */
    private String raw;

    /** 解析结果类型 */
    private RateInputType inputType;

    /** 解析后的数值（十进制字符串） */
    private String value;
}

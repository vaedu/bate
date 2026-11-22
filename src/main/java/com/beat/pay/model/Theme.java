package com.beat.pay.model;

import lombok.Data;

/** 主题颜色 */
@Data
public class Theme {
    /** 主题色 */
    private String themeColor;

    /** 时间文字颜色 */
    private String timeColor;

    /** 金额颜色 */
    private String amountColor;
}

package com.beat.pay.model;

import lombok.Data;

/** 功能开关 */
@Data
public class Switches {
    /** 日历模块 */
    private Boolean calendarEnabled;

    /** 月度汇总 */
    private Boolean monthlySummaryEnabled;

    /** 完成激励弹窗 */
    private Boolean rewardPopupEnabled;

    /** 跨天检测提示 */
    private Boolean crossDayDetect;

    /** 动画总开关 */
    private Boolean animationEnabled;

    /** 欢迎页 */
    private Boolean welcomeEnabled;
}

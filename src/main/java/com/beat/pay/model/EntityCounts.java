package com.beat.pay.model;

import lombok.Data;

/** 各类数据数量 */
@Data
public class EntityCounts {
    /** 设置 */
    private Integer settings;

    /** 工时记录 */
    private Integer workRecords;

    /** 分段 */
    private Integer segments;

    /** 目标 */
    private Integer goals;

    /** 排班 */
    private Integer schedules;

    /** 台账 */
    private Integer payrolls;

    /** 通知 */
    private Integer notifications;
}

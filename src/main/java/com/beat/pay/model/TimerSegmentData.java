package com.beat.pay.model;

import lombok.Data;

/** 计时会话分段（存于 beat_timer_session.segments） */
@Data
public class TimerSegmentData {
    /** 分段序号，从1开始 */
    private Integer segmentNo;

    /** 开始时间（毫秒，UTC） */
    private Long startAt;

    /** 结束时间；进行中为空 */
    private Long endAt;
}

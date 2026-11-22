package com.beat.pay.dto.timer;

import lombok.Data;

/** 计时会话分段 */
@Data
public class TimerSegmentVO {
    /** 分段序号 */
    private Integer segmentNo;

    /** 开始时间 */
    private Long startAt;

    /** 结束时间；进行中为空 */
    private Long endAt;
}

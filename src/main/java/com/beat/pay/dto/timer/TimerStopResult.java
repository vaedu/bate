package com.beat.pay.dto.timer;

import com.beat.pay.dto.record.WorkRecordDTO;
import lombok.Data;

/** 结束结果 */
@Data
public class TimerStopResult {
    /** 会话 */
    private TimerSessionVO session;

    /** 服务端落库后的记录 */
    private WorkRecordDTO record;

    /** 记录对应的同步游标 */
    private String syncCursor;
}

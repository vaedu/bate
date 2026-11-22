package com.beat.pay.dto.timer;

import lombok.Data;

/** 50001 冲突响应携带的数据 */
@Data
public class TimerConflictData {
    /** 当前占用互斥的活动会话 */
    private TimerSessionVO currentSession;
}

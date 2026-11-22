package com.beat.pay.dto.timer;

import com.beat.pay.enums.TimerStatus;
import com.beat.pay.model.PayRule;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** 计时会话 */
@Data
public class TimerSessionVO {
    /** 会话ID */
    private String sessionId;

    /** 状态 */
    private TimerStatus status;

    /** 归属日期 */
    private LocalDate workDate;

    /** 开始时间 */
    private Long startedAt;

    /** 分段 */
    private List<TimerSegmentVO> segments;

    /** 截至 serverTime 的已累计工作时长（毫秒） */
    private Long accumulatedMs;

    /** 会话所属设备 */
    private String startDeviceId;

    /** 所属设备名称 */
    private String startDeviceName;

    /** 最近操作设备 */
    private String lastOperatorDeviceId;

    /** 计薪规则快照 */
    private PayRule ruleSnapshot;

    /** 版本号 */
    private Integer version;

    /** 服务端时间 */
    private Long serverTime;
}

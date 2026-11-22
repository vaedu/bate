package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.beat.pay.enums.TimerStatus;
import com.beat.pay.model.PayRule;
import com.beat.pay.model.TimerSegmentData;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** beat_timer_session */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "beat_timer_session", autoResultMap = true)
public class BeatTimerSession extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属用户（所有查询必须带此条件） */
    private Long userId;

    /** 会话状态 */
    private TimerStatus status;

    /** 归属日期 */
    private LocalDate workDate;

    /** 开始时间 */
    private Long startedAt;

    /** 结束时间 */
    private Long endedAt;

    /** 分段 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<TimerSegmentData> segments;

    /** 会话所属设备 */
    private String startDeviceId;

    /** 所属设备名称 */
    private String startDeviceName;

    /** 最近操作设备 */
    private String lastOperatorDeviceId;

    /** 计薪规则快照 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private PayRule ruleSnapshot;

    /** 版本号 */
    private Integer version;

    /** 结束后生成的记录ID */
    private String recordId;
}

package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.beat.pay.enums.Shift;
import com.beat.pay.enums.WorkSource;
import com.beat.pay.model.PayRule;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** beat_work_record */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "beat_work_record", autoResultMap = true)
public class BeatWorkRecord extends SyncEntity {
    /** 主键 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属用户（所有查询必须带此条件） */
    private Long userId;

    /** 归属日期 */
    private LocalDate workDate;

    /** 开始时间 */
    private Long startAt;

    /** 结束时间 */
    private Long endAt;

    /** 总工时（毫秒） */
    private Long durationMs;

    /** 总金额（分） */
    private Long totalEarnCents;

    /** 班次 */
    private Shift shift;

    /** 标签 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /** 备注 */
    private String note;

    /** 是否跨天 */
    private Boolean crossDay;

    /** 来源 */
    private WorkSource source;

    /** 冲突副本指向的原记录ID */
    private String conflictOf;

    /** 计薪规则快照 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private PayRule ruleSnapshot;
}

package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.beat.pay.model.PricingPart;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** beat_work_segment */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "beat_work_segment", autoResultMap = true)
public class BeatWorkSegment extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属用户（所有查询必须带此条件） */
    private Long userId;

    /** 所属记录 */
    private String recordId;

    /** 分段序号 */
    private Integer segmentNo;

    /** 开始时间 */
    private Long startAt;

    /** 结束时间 */
    private Long endAt;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 分段金额（分） */
    private Long earnCents;

    /** 计价片段 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<PricingPart> parts;
}

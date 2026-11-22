package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.beat.pay.enums.Shift;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** beat_schedule */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "beat_schedule")
public class BeatSchedule extends SyncEntity {
    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户（所有查询必须带此条件） */
    private Long userId;

    /** 日期（同步实体ID） */
    private LocalDate scheduleDate;

    /** 班次 */
    private Shift shift;
}

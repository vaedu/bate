package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** beat_goal */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "beat_goal")
public class BeatGoal extends SyncEntity {
    /** 主键 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属用户（所有查询必须带此条件） */
    private Long userId;

    /** 目标名称 */
    private String name;

    /** 目标金额（分） */
    private Long targetAmountCents;

    /** 目标日期 */
    private LocalDate targetDate;

    /** 达成激励语 */
    private String rewardText;

    /** 首次达成时间 */
    private Long achievedAt;
}

package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** beat_payroll */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "beat_payroll")
public class BeatPayroll extends SyncEntity {
    /** 主键 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属用户（所有查询必须带此条件） */
    private Long userId;

    /** 工资月份 YYYY-MM */
    private String payMonth;

    /** 发薪日 */
    private LocalDate payDate;

    /** 应发（分） */
    private Long grossCents;

    /** 实发（分） */
    private Long netCents;

    /** 是否到账 */
    private Boolean received;

    /** 备注 */
    private String note;
}

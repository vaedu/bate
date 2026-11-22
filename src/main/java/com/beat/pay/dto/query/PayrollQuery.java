package com.beat.pay.dto.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 台账查询（API-DAT-06） */
@Data
@EqualsAndHashCode(callSuper = true)
public class PayrollQuery extends PageQuery {
    /** 起始工资月份 YYYY-MM */
    private String monthFrom;

    /** 结束工资月份 */
    private String monthTo;

    /** 是否到账 */
    private Boolean received;
}

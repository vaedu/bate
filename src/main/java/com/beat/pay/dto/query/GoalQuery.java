package com.beat.pay.dto.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 目标查询（API-DAT-04） */
@Data
@EqualsAndHashCode(callSuper = true)
public class GoalQuery extends PageQuery {
    /** 是否包含墓碑 */
    private Boolean includeDeleted = false;
}

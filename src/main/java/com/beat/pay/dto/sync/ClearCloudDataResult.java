package com.beat.pay.dto.sync;

import com.beat.pay.model.EntityCounts;
import lombok.Data;

/** 清除结果 */
@Data
public class ClearCloudDataResult {
    /** 各类被删除的数据数量 */
    private EntityCounts deleted;

    /** 清除完成时间 */
    private Long clearedAt;

    /** 清除后的同意状态，恒为 false */
    private Boolean consented;
}

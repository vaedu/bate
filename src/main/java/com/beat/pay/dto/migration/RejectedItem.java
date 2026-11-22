package com.beat.pay.dto.migration;

import lombok.Data;

/** 被拒绝条目 */
@Data
public class RejectedItem {
    /** 实体ID */
    private String entityId;

    /** 原因 */
    private String reason;
}

package com.beat.pay.dto.sync;

import com.beat.pay.model.EntityCounts;
import lombok.Data;

/** 同步状态（API-SYNC-03） */
@Data
public class SyncStatusVO {
    /** 服务端最新游标 */
    private String serverCursor;

    /** 最近Push */
    private Long lastPushAt;

    /** 最近Pull */
    private Long lastPullAt;

    /** 云端各类数据数量 */
    private EntityCounts entityCounts;

    /** 是否存在活动计时会话 */
    private Boolean hasActiveTimer;
}

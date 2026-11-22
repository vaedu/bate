package com.beat.pay.dto.sync;

import java.util.List;
import lombok.Data;

/** Pull 结果（API-SYNC-02） */
@Data
public class SyncPullResult {
    /** 变更列表 */
    private List<SyncServerChange> changes;

    /** 本页最后一条游标；客户端写入本地成功后再保存 */
    private String nextCursor;

    /** 是否还有下一页 */
    private Boolean hasMore;

    /** 游标过旧/无效，需全量重拉 */
    private Boolean fullSyncRequired;

    /** 服务端时间 */
    private Long serverTime;
}

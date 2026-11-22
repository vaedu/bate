package com.beat.pay.dto.sync;

import java.util.List;
import lombok.Data;

/** Push 结果 */
@Data
public class SyncPushResult {
    /** 请求ID */
    private String requestId;

    /** 逐条结果 */
    private List<SyncPushItemResult> results;

    /** 提交后服务端最新游标（仅参考） */
    private String serverCursor;

    /** 服务端时间 */
    private Long serverTime;
}

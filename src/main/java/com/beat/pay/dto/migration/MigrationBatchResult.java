package com.beat.pay.dto.migration;

import java.util.List;
import lombok.Data;

/** 批次结果 */
@Data
public class MigrationBatchResult {
    /** 批号 */
    private Integer batchNo;

    /** 新写入 */
    private Integer accepted;

    /** 去重 */
    private Integer deduplicated;

    /** 冲突副本 */
    private Integer conflictCopies;

    /** 被拒绝条目 */
    private List<RejectedItem> rejected;

    /** 任务状态 */
    private MigrationTaskVO task;
}

package com.beat.pay.dto.migration;

import com.beat.pay.enums.MigrationStatus;
import com.beat.pay.model.EntityCounts;
import java.util.List;
import lombok.Data;

/** 确认结果 */
@Data
public class MigrationCommitResult {
    /** COMPLETED/FAILED */
    private MigrationStatus status;

    /** 任务 */
    private MigrationTaskVO task;

    /** 迁移后服务端各类数量 */
    private EntityCounts serverCounts;

    /** 不一致项说明 */
    private List<String> mismatches;

    /** 随后 Pull 可用的游标 */
    private String syncCursor;
}

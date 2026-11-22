package com.beat.pay.dto.migration;

import com.beat.pay.enums.MigrationStatus;
import com.beat.pay.model.EntityCounts;
import lombok.Data;

/** 迁移任务 */
@Data
public class MigrationTaskVO {
    /** 任务ID */
    private String migrationId;

    /** 状态 */
    private MigrationStatus status;

    /** 预期数量 */
    private EntityCounts expectedCounts;

    /** 已收到数量 */
    private EntityCounts receivedCounts;

    /** 新写入条数 */
    private Integer acceptedCount;

    /** 去重条数 */
    private Integer dedupedCount;

    /** 冲突副本条数 */
    private Integer conflictCopyCount;

    /** 已处理的最大连续批号 */
    private Integer lastBatchNo;

    /** 失败原因 */
    private String failureReason;

    /** 创建时间 */
    private Long createdAt;

    /** 更新时间 */
    private Long updatedAt;

    /** 过期时间 */
    private Long expiresAt;
}

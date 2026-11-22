package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.beat.pay.enums.MigrationStatus;
import com.beat.pay.model.EntityCounts;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** beat_migration_task */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "beat_migration_task", autoResultMap = true)
public class BeatMigrationTask extends BaseEntity {
    /** 主键=迁移任务ID */
    @TableId(type = IdType.INPUT)
    private String migrationId;

    /** 所属用户（所有查询必须带此条件） */
    private Long userId;

    /** 设备ID */
    private String deviceId;

    /** 应用版本 */
    private String appVersion;

    /** 本地数据结构版本 */
    private Integer schemaVersion;

    /** 状态 */
    private MigrationStatus status;

    /** 预期数量 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private EntityCounts expectedCounts;

    /** 已收到数量 */
    @TableField(typeHandler = JacksonTypeHandler.class)
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

    /** 任务过期时间 */
    private Long expiresAt;
}

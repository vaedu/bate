package com.beat.pay.dto.migration;

import com.beat.pay.model.EntityCounts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建迁移任务（API-MIG-01） */
@Data
public class MigrationCreateRequest {
    /** 客户端生成的迁移任务ID，重复创建幂等 */
    @NotBlank
    @Size(max = 36)
    private String migrationId;

    /** 设备ID */
    @NotBlank
    private String deviceId;

    /** 应用版本 */
    private String appVersion;

    /** 本地数据结构版本 */
    private Integer schemaVersion;

    /** 游客数据各类数量 */
    @NotNull
    @Valid
    private EntityCounts expectedCounts;
}

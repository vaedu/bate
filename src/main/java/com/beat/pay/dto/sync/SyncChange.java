package com.beat.pay.dto.sync;

import com.beat.pay.enums.EntityType;
import com.beat.pay.enums.SyncOp;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Push 变更项 */
@Data
public class SyncChange {
    /** 变更唯一ID（幂等单位） */
    @NotBlank
    private String changeId;

    /** 实体类型 */
    @NotNull
    private EntityType entityType;

    /** 业务记录ID */
    @NotBlank
    private String entityId;

    /** 操作 */
    @NotNull
    private SyncOp op;

    /** 客户端基于的服务端版本；新建为0 */
    @NotNull
    @Min(0)
    private Integer baseVersion;

    /** 客户端修改时间 */
    private Long clientUpdatedAt;

    /** UPSERT 时为实体内容；DELETE 可空 */
    private JsonNode payload;
}

package com.beat.pay.dto.migration;

import com.beat.pay.enums.EntityType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 迁移条目 */
@Data
public class MigrationItem {
    /** 实体类型 */
    @NotNull
    private EntityType entityType;

    /** 实体ID（UUID；排班为日期；设置为 settings） */
    @NotBlank
    private String entityId;

    /** 客户端修改时间 */
    private Long clientUpdatedAt;

    /** 实体内容 */
    @NotNull
    private JsonNode payload;
}

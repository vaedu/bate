package com.beat.pay.dto.sync;

import com.beat.pay.enums.EntityType;
import com.beat.pay.enums.SyncOp;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/** Pull 变更项 */
@Data
public class SyncServerChange {
    /** 变更序号 */
    private String seq;

    /** 实体类型 */
    private EntityType entityType;

    /** 实体ID */
    private String entityId;

    /** 操作 */
    private SyncOp op;

    /** 版本 */
    private Integer version;

    /** 更新时间 */
    private Long updatedAt;

    /** 来源设备 */
    private String originDeviceId;

    /** UPSERT 时为完整实体 */
    private JsonNode payload;
}

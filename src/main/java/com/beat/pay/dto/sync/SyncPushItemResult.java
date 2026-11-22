package com.beat.pay.dto.sync;

import com.beat.pay.enums.EntityType;
import com.beat.pay.enums.PushStatus;
import com.beat.pay.enums.Resolution;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/** Push 单条结果 */
@Data
public class SyncPushItemResult {
    /** 变更ID */
    private String changeId;

    /** 实体类型 */
    private EntityType entityType;

    /** 实体ID */
    private String entityId;

    /** ACCEPTED/DUPLICATE/CONFLICT/REJECTED */
    private PushStatus status;

    /** 服务端当前版本 */
    private Integer newVersion;

    /** 冲突处理结果 */
    private Resolution resolution;

    /** CONFLICT 时的服务端权威内容 */
    private JsonNode serverPayload;

    /** 生成的冲突副本 */
    private JsonNode conflictCopy;

    /** REJECTED 时的业务错误码 */
    private Integer errorCode;

    /** 错误说明 */
    private String errorMessage;
}

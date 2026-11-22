package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.beat.pay.enums.ReceiptType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** beat_sync_receipt */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "beat_sync_receipt")
public class BeatSyncReceipt extends CreatedOnlyEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户（所有查询必须带此条件） */
    private Long userId;

    /** 幂等记录类型 */
    private ReceiptType receiptType;

    /** 幂等键 */
    private String idemKey;

    /** 请求摘要 */
    private String requestHash;

    /** 首次响应（JSON 文本） */
    private String responseBody;

    /** 首次响应状态码 */
    private Integer httpStatus;

    /** 过期时间 */
    private Long expiresAt;
}

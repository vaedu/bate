package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.beat.pay.enums.EntityType;
import com.beat.pay.enums.SyncOp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** beat_sync_change */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "beat_sync_change")
public class BeatSyncChange extends CreatedOnlyEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户（所有查询必须带此条件） */
    private Long userId;

    /** 实体类型 */
    private EntityType entityType;

    /** 实体ID */
    private String entityId;

    /** 操作 */
    private SyncOp op;

    /** 版本 */
    private Integer version;

    /** 来源设备 */
    private String originDeviceId;

    /** 完整实体快照（JSON 文本）；DELETE 为空 */
    private String payload;
}

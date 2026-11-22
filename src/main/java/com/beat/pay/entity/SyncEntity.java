package com.beat.pay.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 可同步实体的公共字段：版本号（乐观并发）、软删除墓碑、客户端修改时间、来源设备。
 * 注意：不要使用 MyBatis-Plus 的 @Version / @TableLogic，版本与删除语义由同步服务显式控制。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class SyncEntity extends BaseEntity {

    /** 服务端版本号，新建为 1 */
    private Integer version;

    /** 软删除标记（墓碑） */
    private Boolean deleted;

    /** 客户端修改时间（毫秒） */
    private Long clientUpdatedAt;

    /** 产生该变更的设备 */
    private String originDeviceId;
}

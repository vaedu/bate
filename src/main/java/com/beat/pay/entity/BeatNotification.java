package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.beat.pay.enums.NotificationType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** beat_notification */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "beat_notification")
public class BeatNotification extends SyncEntity {
    /** 主键 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属用户（所有查询必须带此条件） */
    private Long userId;

    /** 通知类型 */
    private NotificationType type;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 关联目标 */
    private String goalId;

    /** 是否已读 */
    private Boolean isRead;

    /** 通知产生时间 */
    private Long occurredAt;
}

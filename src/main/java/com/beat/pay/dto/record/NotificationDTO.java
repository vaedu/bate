package com.beat.pay.dto.record;

import com.beat.pay.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 通知 */
@Data
public class NotificationDTO {
    /** 通知ID */
    @NotBlank
    private String id;

    /** 版本号 */
    private Integer version;

    /** 最后修改时间 */
    private Long updatedAt;

    /** 软删除标记 */
    private Boolean deleted;

    /** 通知类型 */
    @NotNull
    private NotificationType type;

    /** 标题 */
    @NotBlank
    private String title;

    /** 内容 */
    private String content;

    /** 关联目标 */
    private String goalId;

    /** 是否已读 */
    private Boolean read;

    /** 通知产生时间 */
    private Long createdAt;
}

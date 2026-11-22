package com.beat.pay.dto.auth;

import com.beat.pay.enums.UserStatus;
import lombok.Data;

/** 用户信息（不含手机号） */
@Data
public class UserInfoVO {
    /** 用户ID（字符串） */
    private String userId;

    /** 昵称，可空 */
    private String nickname;

    /** 头像地址，可空 */
    private String avatarUrl;

    /** 状态 */
    private UserStatus status;

    /** 创建时间 */
    private Long createdAt;

    /** 计划物理删除时间 */
    private Long deletionScheduledAt;
}

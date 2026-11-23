package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.beat.pay.enums.LoginType;
import com.beat.pay.enums.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** sys_user_session */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_user_session")
@AllArgsConstructor
@NoArgsConstructor
public class SysUserSession extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户（所有查询必须带此条件） */
    private Long userId;

    /** 设备ID */
    private String deviceId;

    /** Refresh Token 的 SHA-256 摘要 */
    private String refreshTokenHash;

    /** 上一枚摘要（重放检测） */
    private String previousRefreshTokenHash;

    /** 会话状态 */
    private SessionStatus status;

    /** 登录方式 */
    private LoginType loginType;

    /** Refresh Token 过期时间 */
    private Long refreshExpiresAt;

    /** 最近续期 */
    private Long lastRefreshAt;

    /** 吊销时间 */
    private Long revokedAt;

    /** 吊销原因 */
    private String revokeReason;
}

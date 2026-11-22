package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.beat.pay.enums.UserStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** sys_user */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_user")
public class SysUser extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 昵称 */
    private String nickname;

    /** 头像地址 */
    private String avatarUrl;

    /** 账号状态 */
    private UserStatus status;

    /** 注销申请时间 */
    private Long deletionRequestedAt;

    /** 计划物理删除时间 */
    private Long deletionScheduledAt;

    /** 最近登录 */
    private Long lastLoginAt;

    /** 云同步单独同意时间 */
    private Long syncConsentAt;

    /** 同意时的隐私政策版本 */
    private String syncConsentVersion;
}

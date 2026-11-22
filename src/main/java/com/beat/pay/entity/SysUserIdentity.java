package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** sys_user_identity */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_user_identity")
public class SysUserIdentity extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户（所有查询必须带此条件） */
    private Long userId;

    /** 身份提供方 HUAWEI */
    private String provider;

    /** 账号唯一键 UnionID */
    private String unionId;

    /** 辅助 OpenID */
    private String openId;

    /** 最近登录 */
    private Long lastLoginAt;
}

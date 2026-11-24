package com.beat.pay.enums;

/**
 * 用户状态枚举
 *
 * @author vaedu
 */
public enum UserStatus {
    /** 活跃状态 - 用户正常使用中 */
    ACTIVE,

    /** 待删除状态 - 用户已注销但数据保留期内 */
    PENDING_DELETION,

    /** 已冻结状态 - 用户账号被冻结，无法登录 */
    FROZEN
}

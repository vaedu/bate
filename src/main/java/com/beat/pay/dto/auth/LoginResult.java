package com.beat.pay.dto.auth;

import lombok.Data;

/** 登录结果 */
@Data
public class LoginResult {
    /** Beat计薪自有 Access Token */
    private String accessToken;

    /** Refresh Token（仅登录与续期时返回明文） */
    private String refreshToken;

    /** Bearer */
    private String tokenType;

    /** Access Token 有效期（秒） */
    private Integer expiresIn;

    /** Refresh Token 有效期（秒） */
    private Integer refreshExpiresIn;

    /** 用户信息 */
    private UserInfoVO user;

    /** 是否本次新建账号 */
    private Boolean isNewUser;

    /** 云端是否已有业务数据 */
    private Boolean hasCloudData;

    /** 是否已同意云同步（未同意时客户端保持本地模式） */
    private Boolean syncConsented;

    /** 服务端时间 */
    private Long serverTime;
}

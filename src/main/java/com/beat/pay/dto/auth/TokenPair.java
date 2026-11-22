package com.beat.pay.dto.auth;

import lombok.Data;

/** 续期结果 */
@Data
public class TokenPair {
    /** 新的 Access Token */
    private String accessToken;

    /** 轮换后的新 Refresh Token，旧Token立即作废 */
    private String refreshToken;

    /** Bearer */
    private String tokenType;

    /** Access Token 有效期（秒） */
    private Integer expiresIn;

    /** Refresh Token 有效期（秒） */
    private Integer refreshExpiresIn;

    /** 服务端时间 */
    private Long serverTime;
}

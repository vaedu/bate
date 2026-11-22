package com.beat.pay.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Refresh Token 续期请求（API-AUTH-02） */
@Data
public class RefreshRequest {
    /** Refresh Token */
    @NotBlank
    private String refreshToken;

    /** 设备ID */
    @NotBlank
    private String deviceId;
}

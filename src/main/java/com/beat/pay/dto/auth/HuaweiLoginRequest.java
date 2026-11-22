package com.beat.pay.dto.auth;

import com.beat.pay.dto.device.DeviceInfo;
import com.beat.pay.enums.LoginType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 华为账号登录请求（API-AUTH-01） */
@Data
public class HuaweiLoginRequest {
    /** 授权方式 */
    @NotNull
    private LoginType loginType;

    /** Authorization Code，一次性，换取失败即作废不得重试 */
    @NotBlank
    private String authorizationCode;

    /** 设备信息 */
    @NotNull
    @Valid
    private DeviceInfo device;
}

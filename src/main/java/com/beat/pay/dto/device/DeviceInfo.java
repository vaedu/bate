package com.beat.pay.dto.device;

import com.beat.pay.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 设备信息 */
@Data
public class DeviceInfo {
    /** 客户端生成的设备唯一ID */
    @NotBlank
    @Size(max = 64)
    private String deviceId;

    /** 设备名称 */
    @Size(max = 64)
    private String deviceName;

    /** 设备型号 */
    @Size(max = 64)
    private String deviceModel;

    /** 系统版本 */
    @Size(max = 32)
    private String osVersion;

    /** 应用版本 */
    @NotBlank
    @Size(max = 32)
    private String appVersion;

    /** 设备类型 */
    private DeviceType deviceType;
}

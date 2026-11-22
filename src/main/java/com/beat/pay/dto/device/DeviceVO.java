package com.beat.pay.dto.device;

import com.beat.pay.enums.DeviceStatus;
import com.beat.pay.enums.DeviceType;
import lombok.Data;

/** 已登录设备 */
@Data
public class DeviceVO {
    /** 设备ID */
    private String deviceId;

    /** 名称 */
    private String deviceName;

    /** 型号 */
    private String deviceModel;

    /** 类型 */
    private DeviceType deviceType;

    /** 应用版本 */
    private String appVersion;

    /** 最近活跃 */
    private Long lastActiveAt;

    /** 最近同步 */
    private Long lastSyncAt;

    /** 是否当前设备 */
    private Boolean current;

    /** 状态 */
    private DeviceStatus status;
}

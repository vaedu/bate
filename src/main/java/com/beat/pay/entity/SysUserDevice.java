package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.beat.pay.enums.DeviceStatus;
import com.beat.pay.enums.DeviceType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** sys_user_device */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_user_device")
public class SysUserDevice extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户（所有查询必须带此条件） */
    private Long userId;

    /** 设备ID */
    private String deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 设备型号 */
    private String deviceModel;

    /** 系统版本 */
    private String osVersion;

    /** 应用版本 */
    private String appVersion;

    /** 设备类型 */
    private DeviceType deviceType;

    /** 状态 */
    private DeviceStatus status;

    /** 最近活跃 */
    private Long lastActiveAt;

    /** 最近同步 */
    private Long lastSyncAt;
}

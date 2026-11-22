package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.beat.pay.enums.MotivationSource;
import com.beat.pay.enums.PayMode;
import com.beat.pay.model.CustomRates;
import com.beat.pay.model.Switches;
import com.beat.pay.model.Theme;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** beat_user_settings */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "beat_user_settings", autoResultMap = true)
public class BeatUserSettings extends BaseEntity {
    /** 主键=用户ID */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /** 版本号 */
    private Integer version;

    /** 计薪模式 */
    private PayMode payMode;

    /** 基础时薪（分/小时） */
    private Integer baseRateCents;

    /** 加班阈值（分钟） */
    private Integer overtimeThresholdMinutes;

    /** 自定义费率 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private CustomRates customRates;

    /** 功能开关 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Switches switches;

    /** 计时提醒分钟数 */
    private Integer timerReminderMinutes;

    /** 激励词来源 */
    private MotivationSource motivationSource;

    /** 自定义激励词 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> customMotivations;

    /** 主题颜色 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Theme theme;

    /** 是否完成引导 */
    private Boolean onboarded;

    /** 工具排序 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> toolsOrder;

    /** 隐藏工具 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> toolsHidden;

    /** 字段级最后修改时间 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Long> fieldUpdatedAt;

    /** 客户端修改时间 */
    private Long clientUpdatedAt;

    /** 来源设备 */
    private String originDeviceId;
}

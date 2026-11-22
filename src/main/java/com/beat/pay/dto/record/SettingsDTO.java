package com.beat.pay.dto.record;

import com.beat.pay.enums.MotivationSource;
import com.beat.pay.model.PayRule;
import com.beat.pay.model.Switches;
import com.beat.pay.model.Theme;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** 用户设置 */
@Data
public class SettingsDTO {
    /** 版本号 */
    private Integer version;

    /** 最后修改时间 */
    private Long updatedAt;

    /** 计薪规则 */
    @NotNull
    @Valid
    private PayRule payRule;

    /** 功能开关 */
    private Switches switches;

    /** 计时提醒分钟数，0关闭 */
    private Integer timerReminderMinutes;

    /** 激励词来源 */
    private MotivationSource motivationSource;

    /** 自定义激励词 */
    private List<String> customMotivations;

    /** 主题颜色 */
    private Theme theme;

    /** 是否完成引导 */
    private Boolean onboarded;

    /** 工具排序 */
    private List<Integer> toolsOrder;

    /** 隐藏工具 */
    private List<Integer> toolsHidden;

    /** 字段级最后修改时间 */
    private Map<String, Long> fieldUpdatedAt;
}

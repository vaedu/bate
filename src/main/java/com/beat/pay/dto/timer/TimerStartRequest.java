package com.beat.pay.dto.timer;

import com.beat.pay.model.PayRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/** 开始计时（API-TMR-02） */
@Data
public class TimerStartRequest {
    /** 客户端生成，重复提交幂等 */
    @NotBlank
    @Size(max = 36)
    private String sessionId;

    /** 设备ID */
    @NotBlank
    private String deviceId;

    /** 客户端开始时间 */
    @NotNull
    private Long startedAt;

    /** 归属日期 */
    @NotNull
    private LocalDate workDate;

    /** 计薪规则快照 */
    @NotNull
    @Valid
    private PayRule ruleSnapshot;
}

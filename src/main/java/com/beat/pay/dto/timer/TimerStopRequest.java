package com.beat.pay.dto.timer;

import com.beat.pay.dto.record.WorkRecordDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 结束计时（API-TMR-05） */
@Data
public class TimerStopRequest {
    /** 设备ID */
    @NotBlank
    private String deviceId;

    /** 结束时间（远程结束时为结束方选择的时间） */
    @NotNull
    private Long occurredAt;

    /** 可选：会话版本号 */
    private Integer expectedVersion;

    /** 客户端生成的最终工时记录；record.id 即 recordId */
    @NotNull
    @Valid
    private WorkRecordDTO record;
}

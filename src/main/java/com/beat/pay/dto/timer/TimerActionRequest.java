package com.beat.pay.dto.timer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 暂停/继续/取消 */
@Data
public class TimerActionRequest {
    /** 设备ID */
    @NotBlank
    private String deviceId;

    /** 操作实际发生的客户端时间（离线补报时与请求时间不同） */
    @NotNull
    private Long occurredAt;

    /** 可选：会话版本号，不一致返回 50002 */
    private Integer expectedVersion;
}

package com.beat.pay.dto.sync;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** Push 请求（API-SYNC-01） */
@Data
public class SyncPushRequest {
    /** 请求唯一ID（幂等） */
    @NotBlank
    private String requestId;

    /** 设备ID */
    @NotBlank
    private String deviceId;

    /** 变更列表，单次≤100 */
    @NotNull
    @Size(max = 100)
    @Valid
    private List<SyncChange> changes;
}

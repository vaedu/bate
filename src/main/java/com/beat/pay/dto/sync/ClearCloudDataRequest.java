package com.beat.pay.dto.sync;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 清除云端业务数据（API-SYNC-06） */
@Data
public class ClearCloudDataRequest {
    /** 必须为 true */
    @NotNull
    @AssertTrue
    private Boolean confirm;
}

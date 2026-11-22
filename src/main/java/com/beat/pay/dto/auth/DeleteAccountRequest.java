package com.beat.pay.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 注销账号请求（API-AUTH-06） */
@Data
public class DeleteAccountRequest {
    /** 必须为 true */
    @NotNull
    @AssertTrue
    private Boolean confirm;

    /** 注销原因，可空 */
    @Size(max = 200)
    private String reason;
}

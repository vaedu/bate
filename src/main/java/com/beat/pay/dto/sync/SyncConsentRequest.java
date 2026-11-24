package com.beat.pay.dto.sync;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 开启/撤回云同步同意（API-SYNC-05） */
@Data
public class SyncConsentRequest {
    /** true=同意开启；false=撤回同意 */
    @NotNull
    private Boolean consented;

    /** 同意时展示的隐私政策版本，consented=true 时必填，须等于服务端当前版本 */
    private String policyVersion;
}

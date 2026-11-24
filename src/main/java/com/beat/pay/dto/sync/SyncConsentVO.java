package com.beat.pay.dto.sync;

import lombok.Data;

/** 云同步同意状态 */
@Data
public class SyncConsentVO {
    /** 当前是否有效同意（已同意且版本为当前版本） */
    private Boolean consented;

    /** 同意时间 */
    private Long consentedAt;

    /** 已同意的隐私政策版本 */
    private String consentedPolicyVersion;

    /** 服务端当前隐私政策版本 */
    private String currentPolicyVersion;

    /** 最近一次撤回时间 */
    private Long revokedAt;
}

package com.beat.pay.client;

/** 华为账号服务端调用封装。授权码一次性：换取失败（含超时）即作废，调用方不得重试，应让客户端重新授权 */
public interface HuaweiAccountClient {

    /** @throws com.beat.pay.common.BizException HUAWEI_AUTH_FAILED 授权码无效/过期/已使用/服务异常 */
    HuaweiIdentity exchangeAuthCode(String authorizationCode);
}

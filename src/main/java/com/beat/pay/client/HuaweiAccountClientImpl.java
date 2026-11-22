package com.beat.pay.client;

import com.beat.pay.config.HuaweiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HuaweiAccountClientImpl implements HuaweiAccountClient {

    private final HuaweiProperties props;

    @Override
    public HuaweiIdentity exchangeAuthCode(String authorizationCode) {
        // TODO(待核实)：按华为官方文档实现
        //  1) 用 clientId/clientSecret + 授权码 POST 换取 Access Token 与 UnionID/OpenID（地址见 props.getTokenUrl()）
        //  2) 需要昵称头像时再调用用户信息接口（需授权请求携带 profile scope）
        //  3) 联调时第一时间验证：默认授权范围下 UnionID 是否一定返回
        //  4) 超时 props.getTimeoutMillis()，失败抛 BizException(HUAWEI_AUTH_FAILED)，不重试
        throw new UnsupportedOperationException("TODO: exchangeAuthCode");
    }
}

package com.beat.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 华为账号服务端换取配置；client_secret 仅放服务端环境变量，不入代码库 */
@Data
@ConfigurationProperties(prefix = "beat.huawei")
public class HuaweiProperties {
    private String clientId;
    private String clientSecret;
    /** TODO：授权码换取接口地址，以华为官方文档为准 */
    private String tokenUrl;
    /** TODO：用户信息接口地址（需要昵称头像时），以华为官方文档为准 */
    private String userInfoUrl;
    /** 调用华为服务端的超时（毫秒）；授权码一次性，失败即作废，不重试，所以超时应较短 */
    private int timeoutMillis = 3000;
}

package com.beat.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "beat.jwt")
public class JwtProperties {
    /** HMAC 密钥，至少 32 字节，务必通过环境变量注入 */
    private String secret;
    private String issuer = "beat-pay";
    /** Access Token 有效期（秒），默认 30 分钟 */
    private int accessTtlSeconds = 1800;
    /** Refresh Token 有效期（秒），默认 30 天（滚动） */
    private int refreshTtlSeconds = 2592000;
}

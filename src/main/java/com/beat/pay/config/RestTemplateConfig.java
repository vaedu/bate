package com.beat.pay.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder, HuaweiProperties huaweiProps) {
		// 授权码一次性、失败不重试，所以超时要短；时长取自 beat.huawei.timeout-millis（默认 3000）
		Duration timeout = Duration.ofMillis(huaweiProps.getTimeoutMillis());
		return builder
				.connectTimeout(timeout)
				.readTimeout(timeout)
				.build();
	}
}
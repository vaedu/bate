package com.beat.pay.config;

import com.beat.pay.security.AuthInterceptor;
import com.beat.pay.security.ConsentInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final ConsentInterceptor consentInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // context-path=/api/v1，这里的路径不含前缀
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/huawei/login", "/auth/token/refresh", "/actuator/health");
        // 云同步同意校验：业务数据接口必须已同意；账号管理与同意/清除接口不受限（PRV-010）
        registry.addInterceptor(consentInterceptor)
                .addPathPatterns("/sync/**", "/migrations/**", "/timer/**", "/settings", "/work-records/**",
                        "/goals", "/schedules", "/payrolls", "/notifications", "/devices/**")
                .excludePathPatterns("/sync/consent", "/sync/data");
    }
}

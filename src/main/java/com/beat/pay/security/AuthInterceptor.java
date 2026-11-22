package com.beat.pay.security;

import com.beat.pay.common.BizException;
import com.beat.pay.common.Constants;
import com.beat.pay.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 认证拦截：校验 Bearer Token，写入 UserContext */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(Constants.BEARER_PREFIX)) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        AuthPrincipal principal = jwtUtil.parse(header.substring(Constants.BEARER_PREFIX.length()).trim());
        // TODO：校验 principal.sessionId() 对应会话仍为 ACTIVE（被移除/全部退出后应返回 SESSION_REVOKED），
        //       可加本地短 TTL 缓存以避免每次请求查库；账号 PENDING_DELETION 时除登录外返回 ACCOUNT_RESTRICTED
        UserContext.set(principal);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}

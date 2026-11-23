package com.beat.pay.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beat.pay.common.BizException;
import com.beat.pay.common.Constants;
import com.beat.pay.common.ErrorCode;
import com.beat.pay.entity.SysUser;
import com.beat.pay.entity.SysUserSession;
import com.beat.pay.enums.SessionStatus;
import com.beat.pay.enums.UserStatus;
import com.beat.pay.mapper.SysUserMapper;
import com.beat.pay.mapper.SysUserSessionMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截：校验 Bearer Token，写入 UserContext
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

	private final JwtUtil jwtUtil;
	private final SysUserSessionMapper SessionMapper;
    private final SysUserMapper sysUserMapper;
	@Override
	public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return true;
		}
		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith(Constants.BEARER_PREFIX)) {
			throw new BizException(ErrorCode.UNAUTHENTICATED);
		}
		AuthPrincipal principal = jwtUtil.parse(header.substring(Constants.BEARER_PREFIX.length()).trim());
		Long sessionId = principal.sessionId();
		Long userId = principal.userId();
		LambdaQueryWrapper<SysUserSession> wrapper = new LambdaQueryWrapper<SysUserSession>()
				.eq(SysUserSession::getId, sessionId)
				.eq(SysUserSession::getUserId, userId);
        SysUserSession session = SessionMapper.selectOne(wrapper);
        if (session == null || session.getStatus() != SessionStatus.ACTIVE) {
            throw new BizException(ErrorCode.SESSION_REVOKED);
        }
        SysUser user = sysUserMapper.selectById(principal.userId());
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.ACCOUNT_RESTRICTED);
        }
		UserContext.set(principal);
		return true;
	}

	@Override
	public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler,
	                            Exception ex) {
		UserContext.clear();
	}
}

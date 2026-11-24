package com.beat.pay.security;

import com.beat.pay.common.BizException;
import com.beat.pay.common.ErrorCode;
import com.beat.pay.config.BizProperties;
import com.beat.pay.entity.SysUser;
import com.beat.pay.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 云同步同意校验（PRV-010）：未同意，或同意的隐私政策版本不是当前版本时，业务数据接口一律返回 20009。
 * 生效范围见 WebMvcConfig；/sync/consent、/sync/data、登录退出注销等账号接口不经过它。
 */
@Component
@RequiredArgsConstructor
public class ConsentInterceptor implements HandlerInterceptor {

    private final SysUserMapper userMapper;
    private final BizProperties biz;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 这里按“用户自己的主键”查询，不涉及越权；TODO：加本地短 TTL 缓存，避免每个请求都查库；
        // 同时在此处校验账号状态（PENDING_DELETION/FROZEN -> ACCOUNT_RESTRICTED）
        SysUser user = userMapper.selectById(UserContext.userId());
        String current = biz.getConsent().getCurrentPolicyVersion();
        if (user == null || user.getSyncConsentAt() == null || !current.equals(user.getSyncConsentVersion())) {
            throw new BizException(ErrorCode.SYNC_CONSENT_REQUIRED);
        }
        return true;
    }
}

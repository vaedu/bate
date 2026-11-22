package com.beat.pay.security;

import com.beat.pay.common.BizException;
import com.beat.pay.common.ErrorCode;

public final class UserContext {
    private UserContext() {}

    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();

    public static void set(AuthPrincipal p) { HOLDER.set(p); }

    public static void clear() { HOLDER.remove(); }

    public static AuthPrincipal require() {
        AuthPrincipal p = HOLDER.get();
        if (p == null) throw new BizException(ErrorCode.UNAUTHENTICATED);
        return p;
    }

    public static Long userId() { return require().userId(); }

    public static Long sessionId() { return require().sessionId(); }

    /** 当前请求所属设备（来自 Token，而非请求体） */
    public static String deviceId() { return require().deviceId(); }
}

package com.beat.pay.security;

/** 认证上下文：用户ID只取自这里，不信任客户端提交的 userId */
public record AuthPrincipal(Long userId, Long sessionId, String deviceId) {
}

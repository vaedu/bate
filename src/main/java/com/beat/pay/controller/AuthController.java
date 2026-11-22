package com.beat.pay.controller;

import com.beat.pay.common.Result;
import com.beat.pay.dto.auth.HuaweiLoginRequest;
import com.beat.pay.dto.auth.LoginResult;
import com.beat.pay.dto.auth.RefreshRequest;
import com.beat.pay.dto.auth.TokenPair;
import com.beat.pay.security.UserContext;
import com.beat.pay.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** API-AUTH-01（需求：AUTH-001~004,006,012,013） */
    @PostMapping("/auth/huawei/login")
    public Result<LoginResult> huaweiLogin(@Valid @RequestBody HuaweiLoginRequest req) {
        return Result.ok(authService.huaweiLogin(req));
    }

    /** API-AUTH-02（需求：AUTH-005,007） */
    @PostMapping("/auth/token/refresh")
    public Result<TokenPair> refresh(@Valid @RequestBody RefreshRequest req) {
        return Result.ok(authService.refresh(req));
    }

    /** API-AUTH-03（需求：AUTH-008） */
    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        authService.logout(UserContext.userId(), UserContext.sessionId());
        return Result.ok();
    }

    /** API-AUTH-04（需求：AUTH-009） */
    @PostMapping("/auth/logout-all")
    public Result<Void> logoutAll() {
        authService.logoutAll(UserContext.userId());
        return Result.ok();
    }
}

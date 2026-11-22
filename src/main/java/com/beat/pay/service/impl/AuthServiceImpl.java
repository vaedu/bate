package com.beat.pay.service.impl;

import com.beat.pay.dto.auth.HuaweiLoginRequest;
import com.beat.pay.dto.auth.LoginResult;
import com.beat.pay.dto.auth.RefreshRequest;
import com.beat.pay.dto.auth.TokenPair;
import com.beat.pay.service.AuthService;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    @Override
    public LoginResult huaweiLogin(HuaweiLoginRequest req) {
        // TODO: API-AUTH-01（需求：AUTH-001~004,006,012,013）
        throw new UnsupportedOperationException("TODO: API-AUTH-01");
    }

    @Override
    public TokenPair refresh(RefreshRequest req) {
        // TODO: API-AUTH-02（需求：AUTH-005,007）
        throw new UnsupportedOperationException("TODO: API-AUTH-02");
    }

    @Override
    public void logout(Long userId, Long sessionId) {
        // TODO: API-AUTH-03（需求：AUTH-008）
        throw new UnsupportedOperationException("TODO: API-AUTH-03");
    }

    @Override
    public void logoutAll(Long userId) {
        // TODO: API-AUTH-04（需求：AUTH-009）
        throw new UnsupportedOperationException("TODO: API-AUTH-04");
    }
}

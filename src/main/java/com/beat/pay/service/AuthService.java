package com.beat.pay.service;

import com.beat.pay.dto.auth.HuaweiLoginRequest;
import com.beat.pay.dto.auth.LoginResult;
import com.beat.pay.dto.auth.RefreshRequest;
import com.beat.pay.dto.auth.TokenPair;

public interface AuthService {

    /** API-AUTH-01（需求：AUTH-001~004,006,012,013） */
    LoginResult huaweiLogin(HuaweiLoginRequest req);

    /** API-AUTH-02（需求：AUTH-005,007） */
    TokenPair refresh(RefreshRequest req);

    /** API-AUTH-03（需求：AUTH-008） */
    void logout(Long userId, Long sessionId);

    /** API-AUTH-04（需求：AUTH-009） */
    void logoutAll(Long userId);
}

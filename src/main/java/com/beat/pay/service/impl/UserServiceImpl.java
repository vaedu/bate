package com.beat.pay.service.impl;

import com.beat.pay.dto.auth.DeleteAccountRequest;
import com.beat.pay.dto.auth.UserInfoVO;
import com.beat.pay.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Override
    public UserInfoVO me(Long userId) {
        // TODO: API-AUTH-05（需求：AUTH-010）
        throw new UnsupportedOperationException("TODO: API-AUTH-05");
    }

    @Override
    public UserInfoVO deleteAccount(Long userId, DeleteAccountRequest req) {
        // TODO: API-AUTH-06（需求：AUTH-011）
        throw new UnsupportedOperationException("TODO: API-AUTH-06");
    }
}

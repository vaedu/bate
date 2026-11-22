package com.beat.pay.service;

import com.beat.pay.dto.auth.DeleteAccountRequest;
import com.beat.pay.dto.auth.UserInfoVO;

public interface UserService {

    /** API-AUTH-05（需求：AUTH-010） */
    UserInfoVO me(Long userId);

    /** API-AUTH-06（需求：AUTH-011） */
    UserInfoVO deleteAccount(Long userId, DeleteAccountRequest req);
}

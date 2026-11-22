package com.beat.pay.controller;

import com.beat.pay.common.Result;
import com.beat.pay.dto.auth.DeleteAccountRequest;
import com.beat.pay.dto.auth.UserInfoVO;
import com.beat.pay.security.UserContext;
import com.beat.pay.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** API-AUTH-05（需求：AUTH-010） */
    @GetMapping("/users/me")
    public Result<UserInfoVO> me() {
        return Result.ok(userService.me(UserContext.userId()));
    }

    /** API-AUTH-06（需求：AUTH-011） */
    @DeleteMapping("/users/me")
    public Result<UserInfoVO> deleteAccount(@Valid @RequestBody DeleteAccountRequest req) {
        return Result.ok(userService.deleteAccount(UserContext.userId(), req));
    }
}

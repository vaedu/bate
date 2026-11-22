package com.beat.pay.controller;

import com.beat.pay.common.Constants;
import com.beat.pay.common.Result;
import com.beat.pay.dto.sync.SyncPullResult;
import com.beat.pay.dto.sync.SyncPushRequest;
import com.beat.pay.dto.sync.SyncPushResult;
import com.beat.pay.dto.sync.SyncStatusVO;
import com.beat.pay.security.UserContext;
import com.beat.pay.service.SyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    /** API-SYNC-01（需求：SYNC-001~003,006~009,011） */
    @PostMapping("/sync/push")
    public Result<SyncPushResult> push(@RequestHeader(Constants.HEADER_IDEMPOTENCY_KEY) String idemKey, @Valid @RequestBody SyncPushRequest req) {
        return Result.ok(syncService.push(UserContext.userId(), idemKey, req));
    }

    /** API-SYNC-02（需求：SYNC-004,005,006） */
    @GetMapping("/sync/pull")
    public Result<SyncPullResult> pull(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit, @RequestParam String deviceId) {
        return Result.ok(syncService.pull(UserContext.userId(), cursor, limit, deviceId));
    }

    /** API-SYNC-03（需求：SYNC-013） */
    @GetMapping("/sync/status")
    public Result<SyncStatusVO> status(@RequestParam String deviceId) {
        return Result.ok(syncService.status(UserContext.userId(), deviceId));
    }
}

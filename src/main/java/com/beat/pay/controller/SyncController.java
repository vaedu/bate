package com.beat.pay.controller;

import com.beat.pay.common.Constants;
import com.beat.pay.common.Result;
import com.beat.pay.dto.sync.ClearCloudDataRequest;
import com.beat.pay.dto.sync.ClearCloudDataResult;
import com.beat.pay.dto.sync.SyncConsentRequest;
import com.beat.pay.dto.sync.SyncConsentVO;
import com.beat.pay.dto.sync.SyncPullResult;
import com.beat.pay.dto.sync.SyncPushRequest;
import com.beat.pay.dto.sync.SyncPushResult;
import com.beat.pay.dto.sync.SyncStatusVO;
import com.beat.pay.security.UserContext;
import com.beat.pay.service.SyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    /** API-SYNC-04（需求：PRV-010） */
    @GetMapping("/sync/consent")
    public Result<SyncConsentVO> getConsent() {
        return Result.ok(syncService.getConsent(UserContext.userId()));
    }

    /** API-SYNC-05（需求：PRV-009~011） */
    @PutMapping("/sync/consent")
    public Result<SyncConsentVO> updateConsent(@Valid @RequestBody SyncConsentRequest req) {
        return Result.ok(syncService.updateConsent(UserContext.userId(), req));
    }

    /** API-SYNC-06（需求：PRV-012） */
    @DeleteMapping("/sync/data")
    public Result<ClearCloudDataResult> clearCloudData(@Valid @RequestBody ClearCloudDataRequest req) {
        return Result.ok(syncService.clearCloudData(UserContext.userId(), req));
    }
}

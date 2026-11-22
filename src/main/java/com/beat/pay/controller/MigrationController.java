package com.beat.pay.controller;

import com.beat.pay.common.Constants;
import com.beat.pay.common.Result;
import com.beat.pay.dto.migration.MigrationBatchRequest;
import com.beat.pay.dto.migration.MigrationBatchResult;
import com.beat.pay.dto.migration.MigrationCommitRequest;
import com.beat.pay.dto.migration.MigrationCommitResult;
import com.beat.pay.dto.migration.MigrationCreateRequest;
import com.beat.pay.dto.migration.MigrationTaskVO;
import com.beat.pay.security.UserContext;
import com.beat.pay.service.MigrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;

    /** API-MIG-01（需求：MIG-003,004,008） */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/migrations")
    public Result<MigrationTaskVO> create(@RequestHeader(Constants.HEADER_IDEMPOTENCY_KEY) String idemKey, @Valid @RequestBody MigrationCreateRequest req) {
        return Result.ok(migrationService.create(UserContext.userId(), idemKey, req));
    }

    /** API-MIG-02（需求：MIG-005~008） */
    @PostMapping("/migrations/{migrationId}/batches")
    public Result<MigrationBatchResult> submitBatch(@RequestHeader(Constants.HEADER_IDEMPOTENCY_KEY) String idemKey, @PathVariable String migrationId, @Valid @RequestBody MigrationBatchRequest req) {
        return Result.ok(migrationService.submitBatch(UserContext.userId(), idemKey, migrationId, req));
    }

    /** API-MIG-03（需求：MIG-007,011） */
    @GetMapping("/migrations/{migrationId}")
    public Result<MigrationTaskVO> get(@PathVariable String migrationId) {
        return Result.ok(migrationService.get(UserContext.userId(), migrationId));
    }

    /** API-MIG-04（需求：MIG-009,010） */
    @PostMapping("/migrations/{migrationId}/commit")
    public Result<MigrationCommitResult> commit(@RequestHeader(Constants.HEADER_IDEMPOTENCY_KEY) String idemKey, @PathVariable String migrationId, @Valid @RequestBody MigrationCommitRequest req) {
        return Result.ok(migrationService.commit(UserContext.userId(), idemKey, migrationId, req));
    }
}

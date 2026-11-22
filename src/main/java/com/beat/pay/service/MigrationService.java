package com.beat.pay.service;

import com.beat.pay.dto.migration.MigrationBatchRequest;
import com.beat.pay.dto.migration.MigrationBatchResult;
import com.beat.pay.dto.migration.MigrationCommitRequest;
import com.beat.pay.dto.migration.MigrationCommitResult;
import com.beat.pay.dto.migration.MigrationCreateRequest;
import com.beat.pay.dto.migration.MigrationTaskVO;

public interface MigrationService {

    /** API-MIG-01（需求：MIG-003,004,008） */
    MigrationTaskVO create(Long userId, String idemKey, MigrationCreateRequest req);

    /** API-MIG-02（需求：MIG-005~008） */
    MigrationBatchResult submitBatch(Long userId, String idemKey, String migrationId, MigrationBatchRequest req);

    /** API-MIG-03（需求：MIG-007,011） */
    MigrationTaskVO get(Long userId, String migrationId);

    /** API-MIG-04（需求：MIG-009,010） */
    MigrationCommitResult commit(Long userId, String idemKey, String migrationId, MigrationCommitRequest req);
}

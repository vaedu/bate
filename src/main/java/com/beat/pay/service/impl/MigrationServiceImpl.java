package com.beat.pay.service.impl;

import com.beat.pay.dto.migration.MigrationBatchRequest;
import com.beat.pay.dto.migration.MigrationBatchResult;
import com.beat.pay.dto.migration.MigrationCommitRequest;
import com.beat.pay.dto.migration.MigrationCommitResult;
import com.beat.pay.dto.migration.MigrationCreateRequest;
import com.beat.pay.dto.migration.MigrationTaskVO;
import com.beat.pay.service.MigrationService;
import org.springframework.stereotype.Service;

@Service
public class MigrationServiceImpl implements MigrationService {

    @Override
    public MigrationTaskVO create(Long userId, String idemKey, MigrationCreateRequest req) {
        // TODO: API-MIG-01（需求：MIG-003,004,008）
        throw new UnsupportedOperationException("TODO: API-MIG-01");
    }

    @Override
    public MigrationBatchResult submitBatch(Long userId, String idemKey, String migrationId, MigrationBatchRequest req) {
        // TODO: API-MIG-02（需求：MIG-005~008）
        throw new UnsupportedOperationException("TODO: API-MIG-02");
    }

    @Override
    public MigrationTaskVO get(Long userId, String migrationId) {
        // TODO: API-MIG-03（需求：MIG-007,011）
        throw new UnsupportedOperationException("TODO: API-MIG-03");
    }

    @Override
    public MigrationCommitResult commit(Long userId, String idemKey, String migrationId, MigrationCommitRequest req) {
        // TODO: API-MIG-04（需求：MIG-009,010）
        throw new UnsupportedOperationException("TODO: API-MIG-04");
    }
}

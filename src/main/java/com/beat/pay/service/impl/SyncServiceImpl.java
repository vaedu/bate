package com.beat.pay.service.impl;

import com.beat.pay.dto.sync.SyncPullResult;
import com.beat.pay.dto.sync.SyncPushRequest;
import com.beat.pay.dto.sync.SyncPushResult;
import com.beat.pay.dto.sync.SyncStatusVO;
import com.beat.pay.service.SyncService;
import org.springframework.stereotype.Service;

@Service
public class SyncServiceImpl implements SyncService {

    @Override
    public SyncPushResult push(Long userId, String idemKey, SyncPushRequest req) {
        // TODO: API-SYNC-01（需求：SYNC-001~003,006~009,011）
        throw new UnsupportedOperationException("TODO: API-SYNC-01");
    }

    @Override
    public SyncPullResult pull(Long userId, String cursor, Integer limit, String deviceId) {
        // TODO: API-SYNC-02（需求：SYNC-004,005,006）
        throw new UnsupportedOperationException("TODO: API-SYNC-02");
    }

    @Override
    public SyncStatusVO status(Long userId, String deviceId) {
        // TODO: API-SYNC-03（需求：SYNC-013）
        throw new UnsupportedOperationException("TODO: API-SYNC-03");
    }
}

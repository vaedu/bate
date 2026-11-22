package com.beat.pay.service;

import com.beat.pay.dto.sync.SyncPullResult;
import com.beat.pay.dto.sync.SyncPushRequest;
import com.beat.pay.dto.sync.SyncPushResult;
import com.beat.pay.dto.sync.SyncStatusVO;

public interface SyncService {

    /** API-SYNC-01（需求：SYNC-001~003,006~009,011） */
    SyncPushResult push(Long userId, String idemKey, SyncPushRequest req);

    /** API-SYNC-02（需求：SYNC-004,005,006） */
    SyncPullResult pull(Long userId, String cursor, Integer limit, String deviceId);

    /** API-SYNC-03（需求：SYNC-013） */
    SyncStatusVO status(Long userId, String deviceId);
}

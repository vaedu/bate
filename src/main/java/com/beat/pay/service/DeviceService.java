package com.beat.pay.service;

import com.beat.pay.dto.device.DeviceInfo;
import com.beat.pay.dto.device.DeviceVO;
import java.util.List;

public interface DeviceService {

    /** API-DEV-01（需求：SYNC-012） */
    DeviceVO upsertCurrent(Long userId, Long sessionId, DeviceInfo req);

    /** API-DEV-02（需求：SYNC-012） */
    List<DeviceVO> list(Long userId, String currentDeviceId);

    /** API-DEV-03（需求：SYNC-012） */
    void revoke(Long userId, String currentDeviceId, String deviceId);
}

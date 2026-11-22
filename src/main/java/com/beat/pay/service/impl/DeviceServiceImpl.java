package com.beat.pay.service.impl;

import com.beat.pay.dto.device.DeviceInfo;
import com.beat.pay.dto.device.DeviceVO;
import com.beat.pay.service.DeviceService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DeviceServiceImpl implements DeviceService {

    @Override
    public DeviceVO upsertCurrent(Long userId, Long sessionId, DeviceInfo req) {
        // TODO: API-DEV-01（需求：SYNC-012）
        throw new UnsupportedOperationException("TODO: API-DEV-01");
    }

    @Override
    public List<DeviceVO> list(Long userId, String currentDeviceId) {
        // TODO: API-DEV-02（需求：SYNC-012）
        throw new UnsupportedOperationException("TODO: API-DEV-02");
    }

    @Override
    public void revoke(Long userId, String currentDeviceId, String deviceId) {
        // TODO: API-DEV-03（需求：SYNC-012）
        throw new UnsupportedOperationException("TODO: API-DEV-03");
    }
}

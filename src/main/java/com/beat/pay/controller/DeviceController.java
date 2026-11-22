package com.beat.pay.controller;

import com.beat.pay.common.Result;
import com.beat.pay.dto.device.DeviceInfo;
import com.beat.pay.dto.device.DeviceVO;
import com.beat.pay.security.UserContext;
import com.beat.pay.service.DeviceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /** API-DEV-01（需求：SYNC-012） */
    @PutMapping("/devices/current")
    public Result<DeviceVO> upsertCurrent(@Valid @RequestBody DeviceInfo req) {
        return Result.ok(deviceService.upsertCurrent(UserContext.userId(), UserContext.sessionId(), req));
    }

    /** API-DEV-02（需求：SYNC-012） */
    @GetMapping("/devices")
    public Result<List<DeviceVO>> list() {
        return Result.ok(deviceService.list(UserContext.userId(), UserContext.deviceId()));
    }

    /** API-DEV-03（需求：SYNC-012） */
    @DeleteMapping("/devices/{deviceId}")
    public Result<Void> revoke(@PathVariable String deviceId) {
        deviceService.revoke(UserContext.userId(), UserContext.deviceId(), deviceId);
        return Result.ok();
    }
}

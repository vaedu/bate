package com.beat.pay.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beat.pay.common.BizException;
import com.beat.pay.common.ErrorCode;
import com.beat.pay.dto.device.DeviceInfo;
import com.beat.pay.dto.device.DeviceVO;
import com.beat.pay.entity.SysUserDevice;
import com.beat.pay.entity.SysUserSession;
import com.beat.pay.enums.DeviceStatus;
import com.beat.pay.mapper.SysUserDeviceMapper;
import com.beat.pay.mapper.SysUserSessionMapper;
import com.beat.pay.service.DeviceService;

import java.util.List;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class DeviceServiceImpl implements DeviceService {
	private final SysUserDeviceMapper deviceMapper;
	private final SysUserSessionMapper sessionMapper;

	@Override
	public DeviceVO upsertCurrent(Long userId, Long sessionId, DeviceInfo req) {
		SysUserSession session = sessionMapper.selectOne(new LambdaQueryWrapper<SysUserSession>()
				.eq(SysUserSession::getUserId, userId)
				.eq(SysUserSession::getId, sessionId)
		);
		if (session == null || !session.getDeviceId().equals(req.getDeviceId())) {
			throw new BizException(ErrorCode.FORBIDDEN);   // 只能更新当前登录的这台设备
		}
		long now = System.currentTimeMillis();
		SysUserDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<SysUserDevice>()
				.eq(SysUserDevice::getUserId, userId)
				.eq(SysUserDevice::getDeviceId, req.getDeviceId())
		);
		if (device == null) {
			device = new SysUserDevice();
			device.setUserId(userId);
			device.setDeviceId(req.getDeviceId());
			device.setStatus(DeviceStatus.ACTIVE);
		}
		device.setDeviceName(req.getDeviceName());
		device.setDeviceModel(req.getDeviceModel());
		device.setOsVersion(req.getOsVersion());
		device.setAppVersion(req.getAppVersion());
		device.setDeviceType(req.getDeviceType());
		device.setLastActiveAt(now);
		if (device.getId() == null) deviceMapper.insert(device);
		else deviceMapper.updateById(device);
		return BeanUtil.copyProperties(device, DeviceVO.class);
	}

	@Override
	public List<DeviceVO> list(Long userId, String currentDeviceId) {
		List<SysUserDevice> sysUserDevices = deviceMapper.selectList(new LambdaQueryWrapper<SysUserDevice>()
				.eq(SysUserDevice::getUserId, userId)
				.eq(SysUserDevice::getStatus, DeviceStatus.ACTIVE)
				.orderByDesc(SysUserDevice::getLastActiveAt)
		);
		return sysUserDevices.stream().map(d -> {
			DeviceVO vo = BeanUtil.copyProperties(d, DeviceVO.class);
			vo.setCurrent(vo.getDeviceId().equals(currentDeviceId));
			return vo;
		}).toList();
	}

	@Override
	public void revoke(Long userId, String currentDeviceId, String deviceId) {
		if (currentDeviceId.equals(deviceId)) {
			throw new BizException(ErrorCode.INVALID_PARAM);
		}
		deviceMapper.delete(new LambdaQueryWrapper<SysUserDevice>()
				.eq(SysUserDevice::getUserId, userId)
				.eq(SysUserDevice::getDeviceId, deviceId));

	}
}

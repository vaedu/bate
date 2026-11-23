package com.beat.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.beat.pay.client.HuaweiAccountClient;
import com.beat.pay.client.HuaweiIdentity;
import com.beat.pay.common.BizException;
import com.beat.pay.common.Constants;
import com.beat.pay.common.ErrorCode;
import com.beat.pay.config.JwtProperties;
import com.beat.pay.dto.auth.*;
import com.beat.pay.dto.device.DeviceInfo;
import com.beat.pay.entity.*;
import com.beat.pay.enums.DeviceStatus;
import com.beat.pay.enums.SessionStatus;
import com.beat.pay.enums.UserStatus;
import com.beat.pay.mapper.*;
import com.beat.pay.security.JwtUtil;
import com.beat.pay.service.AuthService;
import com.beat.pay.util.HashUtil;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

@Service
@AllArgsConstructor
public class AuthServiceImpl implements AuthService {
	private HuaweiAccountClient huaweiAccountClient;
	private SysUserIdentityMapper sysUserIdentityMapper;
	private SysUserMapper sysUserMapper;
	private SysUserDeviceMapper deviceMapper;
	private static final SecureRandom RANDOM = new SecureRandom();
	private SysUserSessionMapper sessionMapper;
	private final JwtProperties jwtProps;
	private final JwtUtil jwtUtil;
	private BeatUserSettingsMapper settingsMapper;
	private BeatWorkRecordMapper workRecordMapper;
	@Override
	@Transactional(rollbackFor = Exception.class)
	public LoginResult huaweiLogin(HuaweiLoginRequest req) {
		long now = System.currentTimeMillis();

		// 步骤1: 通过授权码换取华为账号信息
		HuaweiIdentity huaweiIdentity = huaweiAccountClient.exchangeAuthCode(req.getAuthorizationCode());
		// 【修正1】没有 UnionID 就无法确定是哪个账号，按华为授权失败处理，避免带着 null 去查库、建身份
		if (huaweiIdentity == null || huaweiIdentity.unionId() == null || huaweiIdentity.unionId().isBlank()) {
			throw new BizException(ErrorCode.HUAWEI_AUTH_FAILED);
		}

		// 步骤2: 根据 provider + unionId 查询用户身份记录
		// 【修正2】加上 provider 条件，与唯一键 (provider, union_id) 一致；改用 Lambda 写法避免列名写错
		SysUserIdentity huaweiUser = sysUserIdentityMapper.selectOne(new LambdaQueryWrapper<SysUserIdentity>()
				.eq(SysUserIdentity::getProvider, Constants.PROVIDER_HUAWEI)
				.eq(SysUserIdentity::getUnionId, huaweiIdentity.unionId()));

		// 步骤3: 新用户注册或老用户更新
		boolean isNewUser = huaweiUser == null;
		if (isNewUser) {
			// 【修正3】昵称/头像只是展示用，超过列长度就不存，不能因此让登录失败
			// （nickname VARCHAR(64)，avatar_url VARCHAR(512)）
			String nickname = huaweiIdentity.nickname();
			if (nickname != null && nickname.length() > 64) {
				nickname = null;
			}
			String avatarUrl = huaweiIdentity.avatarUrl();
			if (avatarUrl != null && avatarUrl.length() > 512) {
				avatarUrl = null;
			}

			// 3.1 创建新用户主记录
			SysUser sysUser = new SysUser();
			sysUser.setAvatarUrl(avatarUrl);
			sysUser.setNickname(nickname);
			sysUser.setStatus(UserStatus.ACTIVE);
			sysUser.setLastLoginAt(now);
			sysUserMapper.insert(sysUser);

			// 3.2 创建华为身份绑定记录
			huaweiUser = new SysUserIdentity();
			huaweiUser.setUserId(sysUser.getId());
			huaweiUser.setProvider(Constants.PROVIDER_HUAWEI);
			huaweiUser.setUnionId(huaweiIdentity.unionId());
			huaweiUser.setOpenId(huaweiIdentity.openId());
			huaweiUser.setLastLoginAt(now);
			sysUserIdentityMapper.insert(huaweiUser);
		} else {
			// 3.3 老用户：openId 变化时更新（用 Objects.equals，库里的 openId 可能为 null）
			// 另外身份表的最近登录时间每次登录都要更新，不能只在 openId 变化时才更新
			if (!Objects.equals(huaweiUser.getOpenId(), huaweiIdentity.openId())) {
				huaweiUser.setOpenId(huaweiIdentity.openId());
			}

			huaweiUser.setLastLoginAt(now);
			sysUserIdentityMapper.updateById(huaweiUser);
		}

		// 步骤4: 获取用户主记录并检查账号状态
		SysUser user = sysUserMapper.selectById(huaweiUser.getUserId());
		// 兜底：有身份映射却查不到用户属于数据不一致，不要往下走空指针
		if (user == null) {
			throw new BizException(ErrorCode.INTERNAL_ERROR);
		}
		UserStatus status = user.getStatus();

		// 4.1 账号被冻结：拒绝登录
		if (status == UserStatus.FROZEN) {
			throw new BizException(ErrorCode.ACCOUNT_RESTRICTED);
		}

		if (status == UserStatus.PENDING_DELETION) {
			// 4.2 账号处于注销冷静期：撤销注销并恢复激活（同时更新最近登录）
			user.setStatus(UserStatus.ACTIVE);
			user.setDeletionRequestedAt(null);
			user.setDeletionScheduledAt(null);
			sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
					.eq(SysUser::getId, user.getId())
					.set(SysUser::getStatus, UserStatus.ACTIVE)
					.set(SysUser::getDeletionRequestedAt, null)
					.set(SysUser::getDeletionScheduledAt, null)
					.set(SysUser::getLastLoginAt, now)
					.set(SysUser::getUpdatedAt, now));
		} else if (!isNewUser) {
			// 【修正5】正常的老用户也要更新用户表的最近登录时间
			// （原来只有注销冷静期那个分支会更新，普通老用户的 last_login_at 永远停在注册那天）
			// 新用户在 3.1 已经写过 lastLoginAt，不用再更新
			sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
					.eq(SysUser::getId, user.getId())
					.set(SysUser::getLastLoginAt, now)
					.set(SysUser::getUpdatedAt, now));
		}

		// 步骤5: 记录或更新设备信息
		Long userId = user.getId();
		DeviceInfo d = req.getDevice();
		String deviceId = d.getDeviceId();
		SysUserDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<SysUserDevice>()
				.eq(SysUserDevice::getUserId, userId)
				.eq(SysUserDevice::getDeviceId, deviceId));

		// 5.1 新设备：创建记录
		if (device == null) {
			device = new SysUserDevice();
			device.setUserId(userId);
			device.setDeviceId(deviceId);
		}

		// 5.2 更新设备信息（新设备和老设备都需要）
		device.setDeviceName(d.getDeviceName());
		device.setDeviceModel(d.getDeviceModel());
		device.setOsVersion(d.getOsVersion());
		device.setAppVersion(d.getAppVersion());
		device.setDeviceType(d.getDeviceType());
		device.setStatus(DeviceStatus.ACTIVE);   // 被移除过的设备重新登录要恢复
		device.setLastActiveAt(now);

		if (device.getId() == null) deviceMapper.insert(device);
		else deviceMapper.updateById(device);

		// 步骤6: 管理会话及分发token
		byte[] buf = new byte[32];
		RANDOM.nextBytes(buf);
		String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
		String refreshHash = HashUtil.sha256Hex(refreshToken);
		SysUserSession session = sessionMapper.selectOne(new LambdaQueryWrapper<SysUserSession>()
				.eq(SysUserSession::getUserId, userId)
				.eq(SysUserSession::getDeviceId, deviceId));
		long refreshExpiresAt = now + jwtProps.getRefreshTtlSeconds() * 1000L;
		if (session == null) {
			SysUserSession newSession = new SysUserSession();
			newSession.setUserId(userId);
			newSession.setDeviceId(deviceId);
			newSession.setRefreshTokenHash(refreshHash);
			newSession.setStatus(SessionStatus.ACTIVE);
			newSession.setLoginType(req.getLoginType());
			newSession.setRefreshExpiresAt(refreshExpiresAt);
			sessionMapper.insert(newSession);
			session = newSession;
		} else {
			sessionMapper.update(null, new LambdaUpdateWrapper<SysUserSession>()
					.eq(SysUserSession::getId, session.getId())
					.set(SysUserSession::getRefreshTokenHash, refreshHash)
					.set(SysUserSession::getPreviousRefreshTokenHash, null)
					.set(SysUserSession::getStatus, SessionStatus.ACTIVE)
					.set(SysUserSession::getLoginType, req.getLoginType())
					.set(SysUserSession::getRefreshExpiresAt, refreshExpiresAt)
					.set(SysUserSession::getLastRefreshAt, null)   // 【修正6】重新登录视为新会话，清掉上次的续期时间
					.set(SysUserSession::getRevokedAt, null)
					.set(SysUserSession::getRevokeReason, null)
					.set(SysUserSession::getUpdatedAt, now));
		}

		// 步骤7: 签发 Access Token 并组装返回
		String accessToken = jwtUtil.generateAccessToken(userId, session.getId(), deviceId);
		LoginResult result = new LoginResult();
		result.setAccessToken(accessToken);
		result.setRefreshToken(refreshToken);            // 明文，只有这一次返回
		result.setTokenType("Bearer");
		result.setExpiresIn(jwtProps.getAccessTtlSeconds());
		result.setRefreshExpiresIn(jwtProps.getRefreshTtlSeconds());
		result.setUser(toUserInfo(user));
		result.setIsNewUser(isNewUser);
		result.setHasCloudData(!isNewUser && hasCloudData(userId));
		result.setServerTime(now);
		return result;
	}

	@Override
	public TokenPair refresh(RefreshRequest req) {
		// TODO: API-AUTH-02（需求：AUTH-005,007）
		throw new UnsupportedOperationException("TODO: API-AUTH-02");
	}

	@Override
	public void logout(Long userId, Long sessionId) {
		// TODO: API-AUTH-03（需求：AUTH-008）
		throw new UnsupportedOperationException("TODO: API-AUTH-03");
	}

	@Override
	public void logoutAll(Long userId) {
		// TODO: API-AUTH-04（需求：AUTH-009）
		throw new UnsupportedOperationException("TODO: API-AUTH-04");
	}

	private UserInfoVO toUserInfo(SysUser user) {
		UserInfoVO vo = new UserInfoVO();
		vo.setUserId(String.valueOf(user.getId()));   // 雪花 ID 必须转字符串
		vo.setNickname(user.getNickname());
		vo.setAvatarUrl(user.getAvatarUrl());
		vo.setStatus(user.getStatus());
		vo.setCreatedAt(user.getCreatedAt());
		vo.setDeletionScheduledAt(user.getDeletionScheduledAt());
		return vo;
	}

	private boolean hasCloudData(Long userId) {
		boolean hasSettings = settingsMapper.selectCount(new LambdaQueryWrapper<BeatUserSettings>()
				.eq(BeatUserSettings::getUserId, userId)) > 0;
		if (hasSettings) {
			return true;
		}
		return workRecordMapper.selectCount(new LambdaQueryWrapper<BeatWorkRecord>()
				.eq(BeatWorkRecord::getUserId, userId)
				.eq(BeatWorkRecord::getDeleted, false)) > 0;
	}
}

package com.beat.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.beat.pay.Validator.SyncPayloadValidator;
import com.beat.pay.common.BizException;
import com.beat.pay.common.ChangeRejectedException;
import com.beat.pay.common.ErrorCode;
import com.beat.pay.dto.sync.*;
import com.beat.pay.entity.BeatSyncReceipt;
import com.beat.pay.entity.SysUser;
import com.beat.pay.entity.SysUserDevice;
import com.beat.pay.enums.PushStatus;
import com.beat.pay.enums.ReceiptType;
import com.beat.pay.mapper.*;
import com.beat.pay.service.SyncService;
import com.beat.pay.util.IdempotencyHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


@Service
@AllArgsConstructor
public class SyncServiceImpl implements SyncService {
	private final SysUserDeviceMapper deviceMapper;
	private final SysUserMapper userMapper;
	private final IdempotencyHelper idempotencyHelper;
	private final SyncPayloadValidator payloadValidator;
	private final BeatWorkRecordMapper recordMapper;
	private final BeatWorkSegmentMapper segmentMapper;
	private final BeatUserSettingsMapper settingsMapper;
	private final BeatSyncChangeMapper syncChangeMapper;
	private final BeatSyncReceiptMapper receiptMapper;
	private final ObjectMapper objectMapper;
	@Override
	@Transactional(rollbackFor = Exception.class)
	public SyncPushResult push(Long userId, String idemKey, SyncPushRequest req) {
		SysUserDevice userDevice = deviceMapper.selectOne(new LambdaQueryWrapper<SysUserDevice>()
				.eq(SysUserDevice::getUserId, userId)
				.eq(SysUserDevice::getDeviceId, req.getDeviceId()));
		if (userDevice == null) {
			throw new BizException(ErrorCode.FORBIDDEN);
		}
		userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
				.eq(SysUser::getId, userId)
				.last("FOR UPDATE"));
		return idempotencyHelper.execute(userId, ReceiptType.REQUEST, idemKey, req, SyncPushResult.class, () -> {
			List<SyncPushItemResult> results = new ArrayList<>();
			for (SyncChange change : req.getChanges()) {
				try {
					Object parsed = payloadValidator.validateAndParse(change);   // 不合格就抛
					results.add(applyChange(userId, req.getDeviceId(), change, parsed));
				} catch (ChangeRejectedException e) {
					results.add(rejected(change, e.getErrorCode(), e.getMessage()));
				}
			}
			SyncPushResult result = new SyncPushResult();
			result.setRequestId(req.getRequestId());
			result.setResults(results);
			result.setServerTime(System.currentTimeMillis());
			return result;
		});
	}

	private SyncPushItemResult applyChange(Long userId, @NotBlank String deviceId, SyncChange change, Object parsed) {
		// 1. 变更级幂等
		BeatSyncReceipt receipt = receiptMapper.selectOne(new LambdaQueryWrapper<BeatSyncReceipt>()
				.eq(BeatSyncReceipt::getUserId, userId)
				.eq(BeatSyncReceipt::getReceiptType, ReceiptType.CHANGE)
				.eq(BeatSyncReceipt::getIdemKey, change.getChangeId()));
		// 查到了先反序列化json然后把status改为DUPLICATE进行返回，不做任何操作
		if (receipt != null){
			try {
				SyncPushItemResult result = objectMapper.readValue(receipt.getResponseBody(), SyncPushItemResult.class);
				result.setStatus(PushStatus.DUPLICATE);
				return result;
			} catch (JsonProcessingException e) {
				throw new IllegalStateException("变更幂等记录反序列化失败 changeId=" + change.getChangeId(), e);
			}
		}

		return null;
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

	@Override
	public SyncConsentVO getConsent(Long userId) {
		// TODO: API-SYNC-04（需求：PRV-010）
		throw new UnsupportedOperationException("TODO: API-SYNC-04");
	}

	@Override
	public SyncConsentVO updateConsent(Long userId, SyncConsentRequest req) {
		// TODO: API-SYNC-05（需求：PRV-009~011）
		throw new UnsupportedOperationException("TODO: API-SYNC-05");
	}

	@Override
	public ClearCloudDataResult clearCloudData(Long userId, ClearCloudDataRequest req) {
		// TODO: API-SYNC-06（需求：PRV-012）
		throw new UnsupportedOperationException("TODO: API-SYNC-06");
	}

	/**
	 * 构建表示同步变更被拒绝的单条推送结果。
	 *
	 * @param change  被拒绝的同步变更项，用于填充变更 ID、实体类型和实体 ID
	 * @param code    拒绝原因对应的业务错误码
	 * @param message 自定义错误说明；为空时使用错误码默认说明
	 * @return 状态为拒绝的同步推送单条结果
	 */
	private SyncPushItemResult rejected(SyncChange change, ErrorCode code, String message) {
		SyncPushItemResult r = new SyncPushItemResult();
		r.setChangeId(change.getChangeId());
		r.setEntityType(change.getEntityType());
		r.setEntityId(change.getEntityId());
		r.setStatus(PushStatus.REJECTED);
		r.setErrorCode(code.getCode());
		r.setErrorMessage(message != null ? message : code.getMessage());
		return r;
	}
}

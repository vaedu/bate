package com.beat.pay.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.beat.pay.common.BizException;
import com.beat.pay.common.ErrorCode;
import com.beat.pay.config.BizProperties;
import com.beat.pay.entity.BeatSyncReceipt;
import com.beat.pay.enums.ReceiptType;
import com.beat.pay.mapper.BeatSyncReceiptMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 幂等执行器：同一个用户、同一个幂等键，业务逻辑只会执行一次，重复请求直接返回第一次的结果。
 * 记录落在 beat_sync_receipt，唯一键 (user_id, receipt_type, idem_key)。
 * 用法（调用方的事务方法里）：
 *   @Transactional(rollbackFor = Exception.class)
 *   public TimerSessionVO start(Long userId, String idemKey, TimerStartRequest req) {
 *       return idempotencyHelper.execute(userId, ReceiptType.REQUEST, idemKey, req, TimerSessionVO.class,
 *               () -> doStart(userId, req));
 *   }
 */
@Slf4j
@Component
public class IdempotencyHelper {

	private static final int MAX_KEY_LENGTH = 128;

	private final BeatSyncReceiptMapper receiptMapper;
	private final BizProperties bizProps;
	private final ObjectMapper objectMapper;
	/** 只用来算请求摘要：Map 按键排序，保证“内容相同”一定得到相同的 JSON */
	private final ObjectMapper hashMapper;

	public IdempotencyHelper(BeatSyncReceiptMapper receiptMapper, BizProperties bizProps, ObjectMapper objectMapper) {
		this.receiptMapper = receiptMapper;
		this.bizProps = bizProps;
		this.objectMapper = objectMapper;
		this.hashMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	}

	/**
	 * 在调用方已有事务中执行带幂等控制的业务操作。
	 * 该方法会根据用户、幂等记录类型和幂等键创建或复用幂等记录，确保相同幂等键对应的同一请求不会重复执行业务逻辑。
	 * 首次请求会先写入占位记录，再执行业务操作，并在成功后缓存响应结果；重复请求会校验请求摘要一致性，并返回首次请求的响应。
	 * 若幂等键为空、空白或超过允许长度，将抛出业务异常。
	 * 若相同幂等键对应的请求内容不一致，将在重放校验中抛出业务异常。
	 * 业务操作执行失败时不会缓存响应，并会尝试删除占位记录后继续抛出原异常。
	 *
	 * @param userId 用户 ID，用于限定幂等记录所属用户
	 * @param type 幂等记录类型，用于区分不同业务场景
	 * @param idemKey 幂等键，用于识别同一次业务请求
	 * @param request 原始请求对象，用于计算请求摘要并校验重复请求内容是否一致
	 * @param resultType 返回结果类型，用于将已缓存的响应反序列化为目标类型
	 * @param action 首次请求时需要执行的业务操作
	 * @param <T> 业务操作返回值类型
	 * @return 首次业务操作的执行结果，或重复请求命中幂等记录时返回的已缓存结果
	 */
	@Transactional(propagation = Propagation.MANDATORY)   // 必须在调用方的事务里，占位记录和业务数据同生共死
	public <T> T execute(Long userId, ReceiptType type, String idemKey, Object request,
	                     Class<T> resultType, Supplier<T> action) {
		if (idemKey == null || idemKey.isBlank() || idemKey.length() > MAX_KEY_LENGTH) {
			throw new BizException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
		}
		long now = System.currentTimeMillis();
		long expiresAt = now + Duration.ofDays(bizProps.getIdempotency().getRetentionDays()).toMillis();
		String hash = requestHash(request);

		// 1. 先“占位”：插入一条还没有响应内容的记录。
		//    两个相同的请求同时到达时，后到的会卡在这条 INSERT 上，等先到的提交后得到唯一键冲突，
		//    所以业务逻辑不会被执行两次。
		Long receiptId;
		BeatSyncReceipt receipt = new BeatSyncReceipt();
		receipt.setUserId(userId);
		receipt.setReceiptType(type);
		receipt.setIdemKey(idemKey);
		receipt.setRequestHash(hash);
		receipt.setExpiresAt(expiresAt);
		try {
			receiptMapper.insert(receipt);
			receiptId = receipt.getId();
		} catch (DuplicateKeyException e) {
			// 2. 这个键以前出现过：加锁读出那条记录
			//    FOR UPDATE 是“当前读”，能读到刚提交的记录（普通查询在可重复读下可能看不到）
			BeatSyncReceipt existing = receiptMapper.selectOne(new LambdaQueryWrapper<BeatSyncReceipt>()
					.eq(BeatSyncReceipt::getUserId, userId)
					.eq(BeatSyncReceipt::getReceiptType, type)
					.eq(BeatSyncReceipt::getIdemKey, idemKey)
					.last("FOR UPDATE"));
			if (existing == null) {
				// 冲突的记录在这一瞬间消失了（对方回滚），让客户端用同一个键重试即可
				throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "请求冲突，请重试");
			}
			if (existing.getExpiresAt() != null && existing.getExpiresAt() > now) {
				return replay(existing, hash, resultType);
			}
			// 记录已过期但清理任务还没删：把这一行接管过来，当作新请求处理
			receiptMapper.update(null, new LambdaUpdateWrapper<BeatSyncReceipt>()
					.eq(BeatSyncReceipt::getId, existing.getId())
					.eq(BeatSyncReceipt::getUserId, userId)
					.set(BeatSyncReceipt::getRequestHash, hash)
					.set(BeatSyncReceipt::getResponseBody, null)
					.set(BeatSyncReceipt::getHttpStatus, null)
					.set(BeatSyncReceipt::getExpiresAt, expiresAt));
			receiptId = existing.getId();
		}

		// 3. 占位成功，说明是第一次：执行业务
		T result;
		try {
			result = action.get();
		} catch (RuntimeException e) {
			// 业务失败不缓存。事务通常会整体回滚；这里再主动删一次，
			// 避免调用方配置了 noRollbackFor 时留下一条没有响应的占位记录
			receiptMapper.delete(new LambdaQueryWrapper<BeatSyncReceipt>()
					.eq(BeatSyncReceipt::getId, receiptId)
					.eq(BeatSyncReceipt::getUserId, userId));
			throw e;
		}

		// 4. 把响应写回记录，下次重复请求直接用
		receiptMapper.update(null, new LambdaUpdateWrapper<BeatSyncReceipt>()
				.eq(BeatSyncReceipt::getId, receiptId)
				.eq(BeatSyncReceipt::getUserId, userId)
				.set(BeatSyncReceipt::getResponseBody, toJson(result))
				.set(BeatSyncReceipt::getHttpStatus, 200));
		return result;
	}

	/** 重复请求：核对内容是否一致，一致就返回第一次的响应 */
	private <T> T replay(BeatSyncReceipt existing, String hash, Class<T> resultType) {
		if (!hash.equals(existing.getRequestHash())) {
			// 同一个键，请求内容不同：客户端用错了键
			throw new BizException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
		}
		if (existing.getResponseBody() == null) {
			// 正常不会出现：占位和响应在同一个事务里一起提交
			log.warn("幂等记录没有响应内容 userId={} key={}", existing.getUserId(), existing.getIdemKey());
			throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "请求仍在处理，请稍后重试");
		}
		try {
			return objectMapper.readValue(existing.getResponseBody(), resultType);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("幂等响应反序列化失败", e);
		}
	}

	private String requestHash(Object request) {
		try {
			return HashUtil.sha256Hex(hashMapper.writeValueAsString(request));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("请求摘要计算失败", e);
		}
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("幂等响应序列化失败", e);
		}
	}
}
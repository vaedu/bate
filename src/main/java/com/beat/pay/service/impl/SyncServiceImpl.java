package com.beat.pay.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.beat.pay.Validator.SyncPayloadValidator;
import com.beat.pay.common.BizException;
import com.beat.pay.common.ChangeRejectedException;
import com.beat.pay.common.ErrorCode;
import com.beat.pay.config.BizProperties;
import com.beat.pay.converter.WorkRecordConverter;
import com.beat.pay.dto.record.SettingsDTO;
import com.beat.pay.dto.record.WorkRecordDTO;
import com.beat.pay.dto.record.WorkSegmentDTO;
import com.beat.pay.dto.sync.*;
import com.beat.pay.entity.*;
import com.beat.pay.enums.*;
import com.beat.pay.mapper.*;
import com.beat.pay.model.PayRule;
import com.beat.pay.service.SyncService;
import com.beat.pay.util.IdUtils;
import com.beat.pay.util.IdempotencyHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
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
	private final BizProperties bizProps;

	/**
	 * 处理客户端的同步推送请求，将多个数据变更应用到服务端。
	 * 执行过程包括设备校验、幂等性检查、逐条变更应用及游标更新。
	 *
	 * @param userId  当前用户 ID
	 * @param idemKey 请求级幂等键，用于防止同一批变更被重复执行
	 * @param req     同步推送请求体，包含设备 ID 和待应用的变更列表
	 * @return 同步推送结果，包含各变更条目的处理状态、服务端游标和时间戳
	 * @throws BizException 当设备未授权时抛出
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public SyncPushResult push(Long userId, String idemKey, SyncPushRequest req) {
		// 校验设备是否已绑定到当前用户，未绑定则拒绝推送
		SysUserDevice userDevice = deviceMapper.selectOne(new LambdaQueryWrapper<SysUserDevice>()
				.eq(SysUserDevice::getUserId, userId)
				.eq(SysUserDevice::getDeviceId, req.getDeviceId()));
		if (userDevice == null) {
			throw new BizException(ErrorCode.FORBIDDEN);
		}
		// 对用户行加锁，防止同一用户的并发推送互相干扰（特别是版本冲突检测）
		userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
				.eq(SysUser::getId, userId)
				.last("FOR UPDATE"));
		// 请求级幂等：同一 idemKey 只执行一次，重复请求直接返回之前的结果
		return idempotencyHelper.execute(userId, ReceiptType.REQUEST, idemKey, req, SyncPushResult.class, () -> {
			List<SyncPushItemResult> results = new ArrayList<>();
			// 逐条处理变更，确保单条失败不影响其他变更的处理
			for (SyncChange change : req.getChanges()) {
				try {
					// 先校验并解析 payload，不合格会抛出 ChangeRejectedException
					Object parsed = payloadValidator.validateAndParse(change);
					results.add(applyChange(userId, req.getDeviceId(), change, parsed));
				} catch (ChangeRejectedException e) {
					// 单条变更被拒绝时记录日志并标记为 REJECTED，不影响其他变更
					log.warn("变更被拒绝 changeId={} entityType={} entityId={} code={} msg={}",
							change.getChangeId(), change.getEntityType(), change.getEntityId(),
							e.getErrorCode().getCode(), e.getMessage());
					results.add(rejected(change, e.getErrorCode(), e.getMessage()));
				}
			}
			// 计算服务端游标：取该用户在 beat_sync_change 表中的最大 ID，用于客户端下次拉取
			BeatSyncChange lastChange = syncChangeMapper.selectOne(new LambdaQueryWrapper<BeatSyncChange>()
					.eq(BeatSyncChange::getUserId, userId)
					.orderByDesc(BeatSyncChange::getId)
					.last("LIMIT 1"));
			// 更新设备的最后同步时间，用于追踪设备活跃状态
			// 注意：LambdaUpdateWrapper 不会自动填充 updatedAt，需手动设置
			long now = System.currentTimeMillis();
			deviceMapper.update(null, new LambdaUpdateWrapper<SysUserDevice>()
					.eq(SysUserDevice::getUserId, userId)
					.eq(SysUserDevice::getDeviceId, req.getDeviceId())
					.set(SysUserDevice::getLastSyncAt, now)
					.set(SysUserDevice::getUpdatedAt, now));
			SyncPushResult result = new SyncPushResult();
			result.setRequestId(req.getRequestId());
			result.setResults(results);
			result.setServerCursor(lastChange != null ? String.valueOf(lastChange.getId()) : "0");
			result.setServerTime(System.currentTimeMillis());
			return result;
		});
	}

	/**
	 * 应用单个数据变更到服务端，包含变更级幂等检查、实体类型分发及幂等记录保存。
	 * 如果变更已被处理过，则直接返回之前的结果（标记为 DUPLICATE）；
	 * 否则根据实体类型分发到对应的处理逻辑，并在成功后保存幂等记录。
	 *
	 * @param userId   当前用户 ID
	 * @param deviceId 发起变更的设备 ID
	 * @param change   同步变更对象，包含操作类型、实体信息及版本信息
	 * @param parsed   已校验并解析的 payload 对象（WorkRecordDTO 或 SettingsDTO）
	 * @return 该变更的处理结果，包含状态、新版本及冲突解决信息
	 */
	private SyncPushItemResult applyChange(Long userId, String deviceId, SyncChange change, Object parsed) {
		// 变更级幂等检查：通过 changeId 判断此变更是否已被处理过
		BeatSyncReceipt receipt = receiptMapper.selectOne(new LambdaQueryWrapper<BeatSyncReceipt>()
				.eq(BeatSyncReceipt::getUserId, userId)
				.eq(BeatSyncReceipt::getReceiptType, ReceiptType.CHANGE)
				.eq(BeatSyncReceipt::getIdemKey, change.getChangeId()));
		// 如果已处理，反序列化之前的结果并标记为 DUPLICATE 后直接返回，不再重复执行
		if (receipt != null) {
			try {
				SyncPushItemResult result = objectMapper.readValue(receipt.getResponseBody(),
						SyncPushItemResult.class);
				result.setStatus(PushStatus.DUPLICATE);
				return result;
			} catch (JsonProcessingException e) {
				throw new IllegalStateException("变更幂等记录反序列化失败 changeId=" + change.getChangeId(), e);
			}
		}
		long now = System.currentTimeMillis();
		// 根据实体类型分发到对应的处理方法
		SyncPushItemResult result = switch (change.getEntityType()) {
			case WORK_RECORD -> applyWorkRecord(userId, deviceId, change, (WorkRecordDTO) parsed, now);
			case SETTINGS -> applySettings(userId, deviceId, change, (SettingsDTO) parsed, now);
			default -> rejected(change, ErrorCode.INVALID_PARAM, "暂不支持的实体类型");
		};
		// 保存变更级幂等记录，仅记录成功处理的变更（ACCEPTED 或 CONFLICT）
		// REJECTED 的不保存幂等，因为客户端可能修复后重试；DUPLICATE 已提前返回
		if (result != null && result.getStatus() != PushStatus.REJECTED) {
			saveReceipt(userId, change.getChangeId(), result, now);
		}
		return result;
	}

	/**
	 * 应用工作记录的同步变更，根据操作类型和当前记录状态决定处理策略。
	 * 支持 DELETE（删除）、CREATE（创建）和 UPDATE（更新/恢复）三种场景，
	 * 并处理版本冲突、墓碑记录恢复、冲突副本创建等复杂逻辑。
	 *
	 * @param userId   用户 ID
	 * @param deviceId 设备 ID
	 * @param change   同步变更信息
	 * @param dto      解析后的工作记录数据
	 * @param now      当前时间戳
	 * @return 该变更的处理结果
	 */
	private SyncPushItemResult applyWorkRecord(Long userId, String deviceId, SyncChange change,
	                                           WorkRecordDTO dto, long now) {
		// 查询当前记录状态（包括已删除的记录）
		BeatWorkRecord current = recordMapper.selectOne(new LambdaQueryWrapper<BeatWorkRecord>()
				.eq(BeatWorkRecord::getUserId, userId)
				.eq(BeatWorkRecord::getId, change.getEntityId()));

		// E：客户端删除操作
		if (change.getOp() == SyncOp.DELETE) {
			// E1：记录不存在或已是墓碑状态，直接接受（幂等）
			if (current == null || Boolean.TRUE.equals(current.getDeleted())) {
				int version = current == null ? 0 : current.getVersion();
				return accepted(change, version, Resolution.NONE);
			}
			// E2：版本匹配，执行删除（逻辑删除，标记为墓碑）
			if (change.getBaseVersion().equals(current.getVersion())) {
				int newVersion = current.getVersion() + 1;
				// 乐观锁更新：标记为已删除并递增版本号
				int rows = recordMapper.update(null, new LambdaUpdateWrapper<BeatWorkRecord>()
						.eq(BeatWorkRecord::getId, change.getEntityId())
						.eq(BeatWorkRecord::getUserId, userId)
						.eq(BeatWorkRecord::getVersion, current.getVersion())
						.set(BeatWorkRecord::getDeleted, true)
						.set(BeatWorkRecord::getVersion, newVersion)
						.set(BeatWorkRecord::getClientUpdatedAt, change.getClientUpdatedAt())
						.set(BeatWorkRecord::getOriginDeviceId, deviceId)
						.set(BeatWorkRecord::getUpdatedAt, now));
				if (rows != 1) {
					throw new IllegalStateException("删除工时记录失败 entityId=" + change.getEntityId());
				}
				// 删除关联的所有工作分段
				segmentMapper.delete(new LambdaQueryWrapper<BeatWorkSegment>()
						.eq(BeatWorkSegment::getUserId, userId)
						.eq(BeatWorkSegment::getRecordId, change.getEntityId()));
				// DELETE 操作的变更日志无需保存快照（payload = null）
				saveChangeLog(userId, change, SyncOp.DELETE, newVersion, deviceId, null);
				return accepted(change, newVersion, Resolution.NONE);
			}
			// E3：版本不匹配，拒绝删除并返回服务端当前状态
			SyncPushItemResult result = accepted(change, current.getVersion(), Resolution.SERVER_KEPT);
			result.setStatus(PushStatus.CONFLICT);
			result.setServerPayload(snapshotOf(userId, current));
			return result;
		}

		// A：记录不存在，创建新记录
		if (current == null) {
			return createRecord(userId, deviceId, change, dto);
		}

		// B/C/D：记录已存在，判断是墓碑恢复还是正常更新
		boolean tombstone = Boolean.TRUE.equals(current.getDeleted());
		// D：非墓碑且版本不匹配，创建冲突副本（新 UUID）
		if (!tombstone && !change.getBaseVersion().equals(current.getVersion())) {
			// 生成新的 UUID 作为冲突副本的 ID
			String uuid = IdUtils.uuid();
			BeatWorkRecord record = BeanUtil.copyProperties(dto, BeatWorkRecord.class,
					"id", "updatedAt", "conflictOf", "version", "deleted");
			record.setId(uuid);
			record.setUserId(userId);
			record.setVersion(1);
			record.setDeleted(false);
			record.setConflictOf(current.getId());  // 标记冲突来源
			record.setClientUpdatedAt(change.getClientUpdatedAt());
			record.setOriginDeviceId(deviceId);
			if (record.getCrossDay() == null) {
				record.setCrossDay(false);
			}
			if (record.getSource() == null) {
				record.setSource(WorkSource.TIMER);
			}
			recordMapper.insert(record);
			// 冲突副本的分段也需要换新 UUID，避免与原记录的分段冲突
			List<BeatWorkSegment> segs = insertSegments(userId, uuid, dto.getSegments(), true);
			// 变更日志中记录新的 UUID 和冲突副本数据
			saveChangeLog(userId, change, uuid, SyncOp.UPSERT, 1, deviceId, WorkRecordConverter.toDto(record, segs));
			// 返回冲突结果，包含服务端当前记录和新创建的冲突副本
			SyncPushItemResult result = accepted(change, current.getVersion(), Resolution.CONFLICT_COPY_CREATED);
			result.setStatus(PushStatus.CONFLICT);
			result.setServerPayload(snapshotOf(userId, current));  // 服务端的原记录
			result.setConflictCopy(objectMapper.valueToTree(WorkRecordConverter.toDto(record, segs)));  // 新建的副本
			return result;
		}

		// B/C：覆盖更新原记录
		// B：墓碑记录被更新，标记为恢复（Resolution.RESTORED_BY_UPDATE）
		// C：正常更新且版本匹配（Resolution.NONE）
		return overwriteRecord(userId, deviceId, change, dto, current, now,
				tombstone ? Resolution.RESTORED_BY_UPDATE : Resolution.NONE);
	}

	/**
	 * 根据同步变更创建工作记录，并写入对应的工作分段与变更日志。
	 * 在创建前会校验记录 ID 是否已被其他用户占用，以及分段 ID 是否合法。
	 * 创建成功后返回接受结果；如果并发插入导致主键冲突，则返回拒绝结果。
	 *
	 * @param userId   当前用户 ID
	 * @param deviceId 发起同步的设备 ID
	 * @param change   客户端提交的同步变更信息
	 * @param dto      待创建的工作记录数据
	 * @return 同步推送处理结果，包含接受、拒绝及版本等信息
	 */
	private SyncPushItemResult createRecord(Long userId, String deviceId, SyncChange change,
	                                        WorkRecordDTO dto) {
		// 预检1：记录 ID 是否被其他用户占用（记录主键是全局唯一的 UUID）
		Long other = recordMapper.selectCount(new LambdaQueryWrapper<BeatWorkRecord>()
				.ne(BeatWorkRecord::getUserId, userId)
				.eq(BeatWorkRecord::getId, change.getEntityId()));
		if (other > 0) {
			throw new ChangeRejectedException(ErrorCode.INVALID_PARAM);
		}
		// 预检2：分段 ID 是否合法且未被占用
		checkSegmentIds(userId, change.getEntityId(), dto.getSegments());

		BeatWorkRecord record = BeanUtil.copyProperties(dto, BeatWorkRecord.class,
				"updatedAt", "conflictOf", "version", "deleted");
		record.setId(change.getEntityId());
		record.setUserId(userId);
		record.setVersion(1);  // 新建记录初始版本为 1
		record.setDeleted(false);
		record.setClientUpdatedAt(change.getClientUpdatedAt());
		record.setOriginDeviceId(deviceId);
		// 设置默认值
		if (record.getCrossDay() == null) {
			record.setCrossDay(false);
		}
		if (record.getSource() == null) {
			record.setSource(WorkSource.TIMER);
		}
		try {
			recordMapper.insert(record);
		} catch (DuplicateKeyException e) {
			// 并发场景：预检通过后，其他请求抢先插入了相同 ID
			// 此时尚未写入分段和变更日志，可以安全拒绝
			return rejected(change, ErrorCode.INVALID_PARAM, null);
		}
		// 插入分段，使用客户端提供的原始 ID
		List<BeatWorkSegment> segs = insertSegments(userId, change.getEntityId(), dto.getSegments(), false);
		// 记录变更日志，包含完整的新建记录快照
		saveChangeLog(userId, change, SyncOp.UPSERT, 1, deviceId, WorkRecordConverter.toDto(record, segs));
		return accepted(change, 1, Resolution.NONE);
	}


	/**
	 * 覆盖更新指定工时记录，并同步替换其分段数据。
	 * 该方法会先校验待写入的分段 ID，随后基于当前记录版本执行乐观锁更新。
	 * 更新成功后删除原有分段并插入新的分段数据，最后重新读取记录、保存变更日志，
	 * 并返回本次同步推送的处理结果。
	 *
	 * @param userId     用户 ID
	 * @param deviceId   发起同步的设备 ID
	 * @param change     同步变更信息，包含实体 ID、客户端更新时间等数据
	 * @param dto        待覆盖写入的工时记录数据
	 * @param current    当前数据库中的工时记录，用于获取当前版本并进行乐观锁更新
	 * @param now        服务端当前时间戳，用于更新记录的更新时间
	 * @param resolution 本次同步冲突处理或合并决策结果
	 * @return 覆盖更新成功后的同步推送处理结果
	 * @throws IllegalStateException 当记录更新失败或乐观锁条件不匹配时抛出
	 */
	private SyncPushItemResult overwriteRecord(Long userId, String deviceId, SyncChange change,
	                                           WorkRecordDTO dto, BeatWorkRecord current,
	                                           long now, Resolution resolution) {
		// 预检分段 ID，必须在任何写库之前调用，防止分段 ID 冲突导致部分写入成功
		checkSegmentIds(userId, change.getEntityId(), dto.getSegments());

		int newVersion = current.getVersion() + 1;
		// 使用乐观锁更新记录，基于当前版本号进行条件更新
		int rows = recordMapper.update(null, new LambdaUpdateWrapper<BeatWorkRecord>()
				.eq(BeatWorkRecord::getId, change.getEntityId())
				.eq(BeatWorkRecord::getUserId, userId)
				.eq(BeatWorkRecord::getVersion, current.getVersion())
				.set(BeatWorkRecord::getVersion, newVersion)
				.set(BeatWorkRecord::getDeleted, false)
				.set(BeatWorkRecord::getClientUpdatedAt, change.getClientUpdatedAt())
				.set(BeatWorkRecord::getUpdatedAt, now)
				.set(BeatWorkRecord::getOriginDeviceId, deviceId)
				.set(BeatWorkRecord::getWorkDate, dto.getWorkDate())
				.set(BeatWorkRecord::getStartAt, dto.getStartAt())
				.set(BeatWorkRecord::getEndAt, dto.getEndAt())
				.set(BeatWorkRecord::getDurationMs, dto.getDurationMs())
				.set(BeatWorkRecord::getTotalEarnCents, dto.getTotalEarnCents())
				.set(BeatWorkRecord::getShift, dto.getShift())
				.set(BeatWorkRecord::getNote, dto.getNote())
				.set(BeatWorkRecord::getCrossDay, Boolean.TRUE.equals(dto.getCrossDay()))
				.set(BeatWorkRecord::getSource, dto.getSource() != null ? dto.getSource() : WorkSource.TIMER)
				.set(BeatWorkRecord::getTags, dto.getTags() == null ? null : toJson(dto.getTags()))
				.set(BeatWorkRecord::getRuleSnapshot,
						dto.getRuleSnapshot() == null ? null : toJson(dto.getRuleSnapshot())));
		if (rows != 1) {
			throw new IllegalStateException("更新工时记录失败 entityId=" + change.getEntityId());
		}
		// 删除原有分段（墓碑记录的分段通常已删，这里删除操作是幂等的）
		segmentMapper.delete(new LambdaQueryWrapper<BeatWorkSegment>()
				.eq(BeatWorkSegment::getUserId, userId)
				.eq(BeatWorkSegment::getRecordId, change.getEntityId()));
		// 插入新分段，使用原始 ID（不生成新 UUID）
		List<BeatWorkSegment> segs = insertSegments(userId, change.getEntityId(), dto.getSegments(), false);
		// 重新查询记录以获取完整的更新后状态（此时版本已是 newVersion）
		BeatWorkRecord record = recordMapper.selectOne(new LambdaQueryWrapper<BeatWorkRecord>()
				.eq(BeatWorkRecord::getId, change.getEntityId())
				.eq(BeatWorkRecord::getUserId, userId));
		// 保存变更日志，记录完整的更新后快照
		saveChangeLog(userId, change, SyncOp.UPSERT, newVersion, deviceId, WorkRecordConverter.toDto(record, segs));
		return accepted(change, newVersion, resolution);
	}


	/**
	 * 转换并插入工作分段，返回插入的实体列表用于生成快照。
	 *
	 * @param userId   用户 ID
	 * @param recordId 记录 ID
	 * @param segments 待插入的分段数据列表
	 * @param newIds   是否为分段生成新 UUID（冲突副本场景需要为 true，避免 ID 冲突）
	 * @return 已插入的分段实体列表
	 */
	private List<BeatWorkSegment> insertSegments(Long userId, String recordId,
	                                             List<WorkSegmentDTO> segments, boolean newIds) {
		List<BeatWorkSegment> segs = segments.stream().map(seg -> {
			BeatWorkSegment s = toSegmentEntity(userId, recordId, seg);
			// 冲突副本场景下需要为分段生成新 UUID，防止与原记录的分段 ID 冲突
			if (newIds) {
				s.setId(IdUtils.uuid());
			}
			return s;
		}).toList();
		for (BeatWorkSegment seg : segs) {
			segmentMapper.insert(seg);
		}
		return segs;
	}

	/**
	 * 根据已接受的同步变更创建推送结果，并填充变更标识、实体信息、状态、新版本号及冲突解决结果。
	 *
	 * @param change     已接受的同步变更对象，用于获取变更标识、实体类型和实体标识
	 * @param newVersion 变更被接受后生成的新版本号
	 * @param resolution 本次变更对应的解决结果
	 * @return 表示该同步变更已被接受的推送结果
	 */
	private SyncPushItemResult accepted(SyncChange change, int newVersion, Resolution resolution) {
		SyncPushItemResult r = new SyncPushItemResult();
		r.setChangeId(change.getChangeId());
		r.setEntityType(change.getEntityType());
		r.setEntityId(change.getEntityId());
		r.setStatus(PushStatus.ACCEPTED);
		r.setNewVersion(newVersion);
		r.setResolution(resolution);
		return r;
	}

	/**
	 * 将工作分段 DTO 转换为实体对象。
	 * 分段 ID 使用客户端生成的 UUID（主键策略为 IdType.INPUT，不自动生成），
	 * parts 字段由实体上的 JacksonTypeHandler 自动序列化为 JSON 存储。
	 *
	 * @param userId   用户 ID
	 * @param recordId 所属记录 ID
	 * @param seg      分段 DTO
	 * @return 分段实体对象
	 */
	private BeatWorkSegment toSegmentEntity(Long userId, String recordId, WorkSegmentDTO seg) {
		BeatWorkSegment s = new BeatWorkSegment();
		s.setId(seg.getId());
		s.setUserId(userId);
		s.setRecordId(recordId);
		s.setSegmentNo(seg.getSegmentNo());
		s.setStartAt(seg.getStartAt());
		s.setEndAt(seg.getEndAt());
		s.setDurationMs(seg.getDurationMs());
		s.setEarnCents(seg.getEarnCents());
		s.setParts(seg.getParts());  // JacksonTypeHandler 自动转 JSON
		return s;
	}

	/**
	 * 保存变更日志到 beat_sync_change 表，用于客户端的增量拉取。
	 * 日志包含变更的完整快照（UPSERT）或空快照（DELETE）。
	 *
	 * @param userId   用户 ID
	 * @param change   同步变更对象
	 * @param op       操作类型（UPSERT 或 DELETE）
	 * @param version  变更后的版本号
	 * @param deviceId 发起变更的设备 ID
	 * @param snapshot 实体完整快照（WorkRecordDTO/SettingsDTO），DELETE 操作传 null
	 */
	private void saveChangeLog(Long userId, SyncChange change, SyncOp op, Integer version,
	                           String deviceId, Object snapshot) {
		saveChangeLog(userId, change, change.getEntityId(), op, version, deviceId, snapshot);
	}

	/**
	 * 保存变更日志（支持自定义实体 ID，用于冲突副本场景）。
	 *
	 * @param userId   用户 ID
	 * @param change   同步变更对象
	 * @param entityId 实体 ID（可能是原 ID 或新生成的冲突副本 ID）
	 * @param op       操作类型
	 * @param version  版本号
	 * @param deviceId 设备 ID
	 * @param snapshot 实体快照
	 */
	private void saveChangeLog(Long userId, SyncChange change, String entityId, SyncOp op, Integer version,
	                           String deviceId, Object snapshot) {
		BeatSyncChange log = new BeatSyncChange();
		log.setUserId(userId);
		log.setEntityType(change.getEntityType());
		log.setEntityId(entityId);
		log.setOp(op);
		log.setVersion(version);
		log.setOriginDeviceId(deviceId);
		try {
			// 序列化失败属于服务端异常，直接抛 IllegalStateException 让整条 push 回滚
			log.setPayload(snapshot != null ? objectMapper.writeValueAsString(snapshot) : null);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("变更日志序列化失败 changeId=" + change.getChangeId(), e);
		}
		syncChangeMapper.insert(log);
	}

	/**
	 * 应用设置类实体的同步变更。
	 * F：记录不存在，直接创建（version=1）；
	 * G：baseVersion == 当前 version，客户端数据整体接受；
	 * H：版本冲突，按字段级时间戳合并（需求 5.9）。
	 *
	 * @param userId   用户 ID
	 * @param deviceId 设备 ID
	 * @param change   同步变更对象
	 * @param dto      设置数据
	 * @param now      当前时间戳
	 * @return 变更处理结果
	 */
	private SyncPushItemResult applySettings(Long userId, String deviceId, SyncChange change, SettingsDTO dto,
	                                         long now) {
		// 设置每个用户只有一条，主键即 userId
		BeatUserSettings current = settingsMapper.selectById(userId);
		// F：记录不存在，直接创建
		if (current == null) {
			BeatUserSettings created = buildSettings(dto);
			created.setUserId(userId);
			created.setVersion(1);
			created.setClientUpdatedAt(change.getClientUpdatedAt() != null ? change.getClientUpdatedAt() : now);
			created.setOriginDeviceId(deviceId);
			settingsMapper.insert(created);
			saveChangeLog(userId, change, SyncOp.UPSERT, 1, deviceId, toSettingsDto(created));
			return accepted(change, 1, Resolution.NONE);
		}
		// G 整体接受；H 先做字段级合并，得到目标状态
		boolean fastPath = change.getBaseVersion().equals(current.getVersion());
		BeatUserSettings target = fastPath ? buildSettings(dto) : mergeSettings(current, dto, change);
		int newVersion = current.getVersion() + 1;
		// 为防止某些字段在客户端不传的时候为null导致服务器错误
		if (target.getTimerReminderMinutes() == null) target.setTimerReminderMinutes(current.getTimerReminderMinutes());
		if (target.getMotivationSource() == null) target.setMotivationSource(current.getMotivationSource());
		if (target.getOnboarded() == null) target.setOnboarded(current.getOnboarded());
		// fastPath：客户端的字段时间和服务端已有的合并，客户端没传的不要丢
		if (fastPath) {
			Map<String, Long> times = new HashMap<>();
			if (current.getFieldUpdatedAt() != null) {
				times.putAll(current.getFieldUpdatedAt());
			}
			if (dto.getFieldUpdatedAt() != null) {
				times.putAll(dto.getFieldUpdatedAt());
			}
			target.setFieldUpdatedAt(times.isEmpty() ? null : times);
		}
		// 乐观锁更新：version 匹配才生效
		int rows = settingsMapper.update(null, setAllSettingsFields(new LambdaUpdateWrapper<BeatUserSettings>()
				.eq(BeatUserSettings::getUserId, userId)
				.eq(BeatUserSettings::getVersion, current.getVersion()), target)
				.set(BeatUserSettings::getVersion, newVersion)
				.set(BeatUserSettings::getClientUpdatedAt, change.getClientUpdatedAt())
				.set(BeatUserSettings::getOriginDeviceId, deviceId)
				.set(BeatUserSettings::getUpdatedAt, now));
		if (rows != 1) {
			throw new IllegalStateException("更新用户设置失败 userId=" + userId);
		}
		// 用更新后的真实落库状态做变更日志快照，供其他设备 Pull 到合并结果
		SettingsDTO snapshot = toSettingsDto(settingsMapper.selectById(userId));
		saveChangeLog(userId, change, SyncOp.UPSERT, newVersion, deviceId, snapshot);
		if (fastPath) {
			return accepted(change, newVersion, Resolution.NONE);
		}
		// H：冲突已按字段级合并，返回 CONFLICT + 合并后的权威内容，客户端据此对齐本地
		SyncPushItemResult result = accepted(change, newVersion, Resolution.FIELD_MERGED);
		result.setStatus(PushStatus.CONFLICT);
		result.setServerPayload(objectMapper.valueToTree(snapshot));
		return result;
	}

	/** 字段级合并的合并单位（payRule 的四个字段算一个整体） */
	private static final List<String> SETTINGS_MERGE_UNITS = List.of(
			"payRule", "switches", "timerReminderMinutes", "motivationSource",
			"customMotivations", "theme", "onboarded", "toolsOrder", "toolsHidden");

	/**
	 * 版本冲突时的字段级合并（需求 5.9 规则 H）：
	 * 对每个合并单位，clientTime = dto.fieldUpdatedAt[该项]（缺省 change.clientUpdatedAt），
	 * serverTime = current.fieldUpdatedAt[该项]（缺省 current.clientUpdatedAt，再缺省 0）；
	 * clientTime > serverTime 才采用客户端值和时间，否则（含相等）保留服务端。
	 *
	 * @param current 服务端当前设置
	 * @param dto     客户端推送的设置
	 * @param change  同步变更对象
	 * @return 合并后的设置实体（同步元数据由调用方更新）
	 */
	private BeatUserSettings mergeSettings(BeatUserSettings current, SettingsDTO dto, SyncChange change) {
		// 先整体保留服务端值，客户端胜出的合并单位再逐个覆盖
		BeatUserSettings merged = BeanUtil.copyProperties(current, BeatUserSettings.class);
		Map<String, Long> mergedTimes = new HashMap<>();
		if (current.getFieldUpdatedAt() != null) {
			mergedTimes.putAll(current.getFieldUpdatedAt());
		}
		for (String unit : SETTINGS_MERGE_UNITS) {
			long clientTime = fieldTime(dto.getFieldUpdatedAt(), unit, change.getClientUpdatedAt());
			long serverTime = fieldTime(current.getFieldUpdatedAt(), unit, current.getClientUpdatedAt());
			if (clientTime > serverTime) {
				mergedTimes.put(unit, clientTime);
				applyClientUnit(merged, dto, unit);
			}
		}
		merged.setFieldUpdatedAt(mergedTimes);
		return merged;
	}

	/**
	 * 读取某个合并单位的最后修改时间：
	 * 优先取字段级时间戳，没有则回退整体时间戳，再没有为 0。
	 *
	 * @param fieldUpdatedAt 字段级时间戳（可能为 null）
	 * @param unit           合并单位名
	 * @param fallback       整体修改时间（可能为 null）
	 * @return 该单位的最后修改时间
	 */
	private long fieldTime(Map<String, Long> fieldUpdatedAt, String unit, Long fallback) {
		Long time = fieldUpdatedAt != null ? fieldUpdatedAt.get(unit) : null;
		if (time == null) {
			time = fallback;
		}
		return time != null ? time : 0L;
	}

	/**
	 * 把客户端胜出的合并单位的值覆盖到合并结果上（payRule 四个字段整体覆盖）。
	 *
	 * @param merged 合并结果实体
	 * @param dto    客户端设置
	 * @param unit   合并单位名
	 */
	private void applyClientUnit(BeatUserSettings merged, SettingsDTO dto, String unit) {
		switch (unit) {
			case "payRule" -> {
				PayRule payRule = dto.getPayRule();
				merged.setPayMode(payRule.getPayMode());
				merged.setBaseRateCents(payRule.getBaseRateCents());
				merged.setOvertimeThresholdMinutes(payRule.getOvertimeThresholdMinutes());
				merged.setCustomRates(payRule.getCustomRates());
			}
			case "switches" -> merged.setSwitches(dto.getSwitches());
			case "timerReminderMinutes" -> merged.setTimerReminderMinutes(dto.getTimerReminderMinutes());
			case "motivationSource" -> merged.setMotivationSource(dto.getMotivationSource());
			case "customMotivations" -> merged.setCustomMotivations(dto.getCustomMotivations());
			case "theme" -> merged.setTheme(dto.getTheme());
			case "onboarded" -> merged.setOnboarded(dto.getOnboarded());
			case "toolsOrder" -> merged.setToolsOrder(dto.getToolsOrder());
			case "toolsHidden" -> merged.setToolsHidden(dto.getToolsHidden());
			default -> { /* 未知合并单位不处理 */ }
		}
	}

	/**
	 * 为更新语句显式设置所有设置字段（包括置 null，避免实体更新跳过 null 值导致旧值残留）。
	 *
	 * @param wrapper 更新条件构造器
	 * @param s       目标设置状态
	 * @return 追加了全部字段 SET 子句的更新条件构造器
	 */
	private LambdaUpdateWrapper<BeatUserSettings> setAllSettingsFields(LambdaUpdateWrapper<BeatUserSettings> wrapper,
	                                                                   BeatUserSettings s) {
		return wrapper
				.set(BeatUserSettings::getPayMode, s.getPayMode())
				.set(BeatUserSettings::getBaseRateCents, s.getBaseRateCents())
				.set(BeatUserSettings::getOvertimeThresholdMinutes, s.getOvertimeThresholdMinutes())
				.set(BeatUserSettings::getCustomRates, jsonOrNull(s.getCustomRates()))
				.set(BeatUserSettings::getSwitches, jsonOrNull(s.getSwitches()))
				.set(BeatUserSettings::getTimerReminderMinutes, s.getTimerReminderMinutes())
				.set(BeatUserSettings::getMotivationSource, s.getMotivationSource())
				.set(BeatUserSettings::getCustomMotivations, jsonOrNull(s.getCustomMotivations()))
				.set(BeatUserSettings::getTheme, jsonOrNull(s.getTheme()))
				.set(BeatUserSettings::getOnboarded, s.getOnboarded())
				.set(BeatUserSettings::getToolsOrder, jsonOrNull(s.getToolsOrder()))
				.set(BeatUserSettings::getToolsHidden, jsonOrNull(s.getToolsHidden()))
				.set(BeatUserSettings::getFieldUpdatedAt, jsonOrNull(s.getFieldUpdatedAt()));
	}

	/**
	 * 根据设置 DTO 构建设置实体。
	 *
	 * @param dto 设置数据
	 * @return 设置实体
	 */
	private BeatUserSettings buildSettings(SettingsDTO dto) {
		PayRule payRule = dto.getPayRule();
		BeatUserSettings newSettings = new BeatUserSettings();
		newSettings.setPayMode(payRule.getPayMode());
		newSettings.setBaseRateCents(payRule.getBaseRateCents());
		newSettings.setOvertimeThresholdMinutes(payRule.getOvertimeThresholdMinutes());
		newSettings.setCustomRates(payRule.getCustomRates());
		newSettings.setSwitches(dto.getSwitches());
		newSettings.setTimerReminderMinutes(dto.getTimerReminderMinutes());
		newSettings.setMotivationSource(dto.getMotivationSource());
		newSettings.setCustomMotivations(dto.getCustomMotivations());
		newSettings.setTheme(dto.getTheme());
		newSettings.setOnboarded(dto.getOnboarded());
		newSettings.setToolsOrder(dto.getToolsOrder());
		newSettings.setToolsHidden(dto.getToolsHidden());
		newSettings.setFieldUpdatedAt(dto.getFieldUpdatedAt());
		return newSettings;
	}
	private String jsonOrNull(Object v) {
		return v == null ? null : toJson(v);
	}
	private SettingsDTO toSettingsDto(BeatUserSettings s) {
		SettingsDTO dto = new SettingsDTO();
		dto.setVersion(s.getVersion());
		dto.setUpdatedAt(s.getUpdatedAt());

		PayRule rule = new PayRule();
		rule.setPayMode(s.getPayMode());
		rule.setBaseRateCents(s.getBaseRateCents());
		rule.setOvertimeThresholdMinutes(s.getOvertimeThresholdMinutes());
		rule.setCustomRates(s.getCustomRates());
		dto.setPayRule(rule);

		dto.setSwitches(s.getSwitches());
		dto.setTimerReminderMinutes(s.getTimerReminderMinutes());
		dto.setMotivationSource(s.getMotivationSource());
		dto.setCustomMotivations(s.getCustomMotivations());
		dto.setTheme(s.getTheme());
		dto.setOnboarded(s.getOnboarded());
		dto.setToolsOrder(s.getToolsOrder());
		dto.setToolsHidden(s.getToolsHidden());
		dto.setFieldUpdatedAt(s.getFieldUpdatedAt());
		return dto;
	}
	/**
	 * 拉取服务端的增量变更，客户端通过游标定位起始位置。
	 * TODO: 待实现（API-SYNC-02，对应需求 SYNC-004,005,006）
	 *
	 * @param userId   用户 ID
	 * @param cursor   客户端上次拉取的游标位置
	 * @param limit    本次拉取的最大变更数量
	 * @param deviceId 设备 ID
	 * @return 增量变更列表和新的游标
	 */
	@Override
	public SyncPullResult pull(Long userId, String cursor, Integer limit, String deviceId) {
		// TODO: API-SYNC-02（需求：SYNC-004,005,006）
		throw new UnsupportedOperationException("TODO: API-SYNC-02");
	}

	/**
	 * 查询同步状态信息，包括待拉取的变更数量和设备最后同步时间。
	 * TODO: 待实现（API-SYNC-03，对应需求 SYNC-013）
	 *
	 * @param userId   用户 ID
	 * @param deviceId 设备 ID
	 * @return 同步状态视图对象
	 */
	@Override
	public SyncStatusVO status(Long userId, String deviceId) {
		// TODO: API-SYNC-03（需求：SYNC-013）
		throw new UnsupportedOperationException("TODO: API-SYNC-03");
	}

	/**
	 * 获取用户的云同步授权状态。
	 *
	 * @param userId 用户 ID
	 * @return 授权状态视图对象
	 */
	@Override
	public SyncConsentVO getConsent(Long userId) {
		return toVo(requireUser(userId));
	}

	/**
	 * 更新用户的云同步授权状态。
	 * 用户同意时需校验隐私政策版本，撤销时清空授权记录并记录撤销时间。
	 *
	 * @param userId 用户 ID
	 * @param req    授权请求，包含同意状态和政策版本
	 * @return 更新后的授权状态视图对象
	 * @throws BizException 当隐私政策版本不匹配时抛出
	 */
	@Override
	public SyncConsentVO updateConsent(Long userId, SyncConsentRequest req) {
		long now = System.currentTimeMillis();
		if (Boolean.TRUE.equals(req.getConsented())) {
			// 校验隐私政策版本：常量放前面避免空指针，req.getPolicyVersion() 为 null 时直接判为不匹配
			if (!bizProps.getConsent().getCurrentPolicyVersion().equals(req.getPolicyVersion())) {
				throw new BizException(ErrorCode.INVALID_PARAM, "隐私政策版本不匹配");
			}
			// 记录同意时间、政策版本，并清空撤销时间
			userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
					.eq(SysUser::getId, userId)
					.set(SysUser::getSyncConsentAt, now)
					.set(SysUser::getSyncConsentVersion, req.getPolicyVersion())
					.set(SysUser::getSyncConsentRevokedAt, null)
					.set(SysUser::getUpdatedAt, now));
		} else {
			// 撤销授权：清空同意记录，记录撤销时间
			userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
					.eq(SysUser::getId, userId)
					.set(SysUser::getSyncConsentAt, null)
					.set(SysUser::getSyncConsentVersion, null)
					.set(SysUser::getSyncConsentRevokedAt, now)
					.set(SysUser::getUpdatedAt, now));
		}
		return toVo(requireUser(userId));
	}

	/**
	 * 查询用户并校验用户存在性。
	 *
	 * @param userId 用户 ID
	 * @return 用户实体
	 * @throws BizException 当用户不存在时抛出未认证异常
	 */
	private SysUser requireUser(Long userId) {
		SysUser user = userMapper.selectById(userId);
		if (user == null) {
			throw new BizException(ErrorCode.UNAUTHENTICATED);
		}
		return user;
	}

	/**
	 * 将用户实体转换为授权状态视图对象。
	 * 判断授权是否有效：需同时满足授权时间存在且政策版本与当前版本一致。
	 *
	 * @param user 用户实体
	 * @return 授权状态视图对象
	 */
	private SyncConsentVO toVo(SysUser user) {
		String current = bizProps.getConsent().getCurrentPolicyVersion();
		SyncConsentVO vo = new SyncConsentVO();
		// 授权有效的条件：有授权时间且政策版本匹配当前版本
		vo.setConsented(user.getSyncConsentAt() != null && current.equals(user.getSyncConsentVersion()));
		vo.setConsentedAt(user.getSyncConsentAt());
		vo.setConsentedPolicyVersion(user.getSyncConsentVersion());
		vo.setCurrentPolicyVersion(current);
		vo.setRevokedAt(user.getSyncConsentRevokedAt());
		return vo;
	}

	/**
	 * 清除用户的云端数据，支持用户注销账号或隐私数据清理场景。
	 *
	 * @param userId 用户 ID
	 * @param req    清除数据请求参数
	 * @return 清除结果
	 */
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

	/**
	 * 分段 ID 预检：确保分段 ID 的合法性，防止 ID 冲突和重复。
	 * 检查两个条件：
	 * 1. 同一记录内分段 ID 不能重复
	 * 2. 分段 ID 不能被其他用户或其他记录占用
	 * 该方法必须在任何写库操作之前调用，校验不通过会抛出 ChangeRejectedException。
	 *
	 * @param userId   用户 ID
	 * @param recordId 记录 ID
	 * @param segments 待检查的分段列表
	 * @throws ChangeRejectedException 当分段 ID 重复或已被占用时抛出
	 */
	private void checkSegmentIds(Long userId, String recordId, List<WorkSegmentDTO> segments) {
		List<String> segIds = segments.stream().map(WorkSegmentDTO::getId).toList();
		if (segIds.isEmpty()) {
			return;
		}
		// 检查同一批次内是否有重复的分段 ID
		if (segIds.stream().distinct().count() != segIds.size()) {
			throw new ChangeRejectedException(ErrorCode.INVALID_PARAM);
		}
		// 检查这些 ID 是否被其他用户或其他记录占用
		Long conflict = segmentMapper.selectCount(new LambdaQueryWrapper<BeatWorkSegment>()
				.in(BeatWorkSegment::getId, segIds)
				.and(w -> w.ne(BeatWorkSegment::getUserId, userId)
						.or().ne(BeatWorkSegment::getRecordId, recordId)));
		if (conflict > 0) {
			throw new ChangeRejectedException(ErrorCode.INVALID_PARAM);
		}
	}

	/**
	 * 保存变更级幂等记录，用于防止同一变更被重复执行。
	 * 记录包含变更 ID、处理结果和过期时间，客户端重试时可直接返回缓存的结果。
	 *
	 * @param userId   用户 ID
	 * @param changeId 变更 ID（幂等键）
	 * @param result   变更处理结果
	 * @param now      当前时间戳
	 * @throws IllegalStateException 当结果序列化失败时抛出
	 */
	private void saveReceipt(Long userId, String changeId, SyncPushItemResult result, long now) {
		BeatSyncReceipt r = new BeatSyncReceipt();
		r.setUserId(userId);
		r.setReceiptType(ReceiptType.CHANGE);
		r.setIdemKey(changeId);
		try {
			r.setResponseBody(objectMapper.writeValueAsString(result));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("变更幂等记录序列化失败 changeId=" + changeId, e);
		}
		r.setHttpStatus(200);
		// 设置过期时间，过期后幂等记录可被清理
		r.setExpiresAt(now + Duration.ofDays(bizProps.getIdempotency().getRetentionDays()).toMillis());
		receiptMapper.insert(r);
	}
	/**
	 * 生成工作记录的完整快照，包含记录本身和所有关联分段。
	 * 快照用于在冲突场景下向客户端返回服务端的当前状态。
	 *
	 * @param userId 用户 ID
	 * @param record 工作记录实体
	 * @return 包含记录和分段的 JSON 树节点
	 */
	private JsonNode snapshotOf(Long userId, BeatWorkRecord record) {
		// 查询该记录的所有分段，按序号升序排列
		List<BeatWorkSegment> segs = segmentMapper.selectList(new LambdaQueryWrapper<BeatWorkSegment>()
				.eq(BeatWorkSegment::getUserId, userId)
				.eq(BeatWorkSegment::getRecordId, record.getId())
				.orderByAsc(BeatWorkSegment::getSegmentNo));
		return objectMapper.valueToTree(WorkRecordConverter.toDto(record, segs));
	}
	/**
	 * 将对象序列化为 JSON 字符串。
	 *
	 * @param obj 待序列化的对象
	 * @return JSON 字符串
	 * @throws IllegalStateException 序列化失败时抛出
	 */
	private String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("对象序列化为 JSON 失败", e);
		}
	}
}
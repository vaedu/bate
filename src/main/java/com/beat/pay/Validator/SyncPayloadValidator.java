package com.beat.pay.Validator;

import com.beat.pay.common.ChangeRejectedException;
import com.beat.pay.common.Constants;
import com.beat.pay.common.ErrorCode;
import com.beat.pay.config.BizProperties;
import com.beat.pay.dto.record.SettingsDTO;
import com.beat.pay.dto.record.WorkRecordDTO;
import com.beat.pay.dto.record.WorkSegmentDTO;
import com.beat.pay.dto.sync.SyncChange;
import com.beat.pay.enums.EntityType;
import com.beat.pay.enums.PayMode;
import com.beat.pay.enums.RateInputType;
import com.beat.pay.enums.SyncOp;
import com.beat.pay.model.CustomRates;
import com.beat.pay.model.PayRule;
import com.beat.pay.model.PricingPart;
import com.beat.pay.model.RateInput;
import com.beat.pay.util.PricingUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Push 里单条变更的校验：只检查，不写库。
 * 不合格就抛 ChangeRejectedException，调用方捕获后转成 REJECTED 结果。
 * 三层检查，由外到内：
 *   1. 把 payload（一段 JSON）转成 DTO，格式不对就拒绝
 *   2. 用 DTO 上的注解（@NotNull、@Min、@Size…）校验
 *   3. 业务规则（需求 5.6）：时间、分段、金额
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SyncPayloadValidator {

	private static final int MAX_SEGMENTS = 200;
	private static final int MAX_PARTS_PER_SEGMENT = 10;
	private static final int MAX_TAGS = 20;
	private static final int MAX_TAG_LENGTH = 50;
	private static final int MAX_BASE_RATE_CENTS = 1_000_000;   // 1 万元/小时，防止离谱数值
	private static final int MAX_MINUTES = 24 * 60;
	private static final int MAX_LIST_SIZE = 100;
	private static final int MAX_MOTIVATION_LENGTH = 200;

	private final ObjectMapper objectMapper;
	private final Validator validator;
	private final BizProperties bizProps;

	/**
	 * @return UPSERT 返回解析好的 DTO（WorkRecordDTO 或 SettingsDTO）；DELETE 返回 null（删除不需要内容）
	 */
	public Object validateAndParse(SyncChange change) {
		EntityType type = change.getEntityType();
		if (type != EntityType.WORK_RECORD && type != EntityType.SETTINGS) {
			throw fail(ErrorCode.INVALID_PARAM, "暂不支持的实体类型：" + type);
		}

		if (change.getOp() == SyncOp.DELETE) {
			if (type == EntityType.SETTINGS) {
				throw fail(ErrorCode.INVALID_PARAM, "设置不能删除");
			}
			if (change.getEntityId().length() > 36) {
				throw fail(ErrorCode.INVALID_PARAM, "entityId 过长");
			}
			return null;
		}

		return switch (type) {
			case WORK_RECORD -> parseWorkRecord(change);
			case SETTINGS -> parseSettings(change);
			default -> throw fail(ErrorCode.INVALID_PARAM, "暂不支持的实体类型：" + type);
		};
	}

	// ------------------------------------------------------------------
	// 工时记录
	// ------------------------------------------------------------------

	private WorkRecordDTO parseWorkRecord(SyncChange change) {
		WorkRecordDTO dto = readPayload(change.getPayload(), WorkRecordDTO.class);   // 第 1 层
		checkAnnotations(dto);                                                       // 第 2 层
		if (!change.getEntityId().equals(dto.getId())) {                             // 第 3 层
			throw fail(ErrorCode.INVALID_PARAM, "payload.id 与 entityId 不一致");
		}
		checkTime(dto);
		checkSegments(dto);
		checkAmount(dto);
		checkTags(dto);
		return dto;
	}

	/** 需求 5.6「时间顺序」「时间合理性」 */
	private void checkTime(WorkRecordDTO dto) {
		BizProperties.Timer timer = bizProps.getTimer();
		long now = System.currentTimeMillis();

		if (dto.getStartAt() <= 0) {
			throw fail(ErrorCode.TIMER_TIME_INVALID, "开始时间不合法");
		}
		if (dto.getEndAt() < dto.getStartAt()) {
			throw fail(ErrorCode.TIMER_TIME_INVALID, "结束时间早于开始时间");
		}
		// 不得晚于服务端时间 + 5 分钟（客户端时钟可能略快）
		long latest = now + Duration.ofMinutes(timer.getFutureToleranceMinutes()).toMillis();
		if (dto.getEndAt() > latest) {
			throw fail(ErrorCode.TIMER_TIME_INVALID, "结束时间晚于服务端时间");
		}
		// 单条记录总跨度不超过 48 小时
		long maxSpan = Duration.ofHours(timer.getMaxRecordSpanHours()).toMillis();
		if (dto.getEndAt() - dto.getStartAt() > maxSpan) {
			throw fail(ErrorCode.TIMER_TIME_INVALID, "记录跨度超过上限");
		}
		// 归属日期由客户端按设备时区算，服务端不重算，只检查不离谱（时区最多差一天）
		LocalDate startDayUtc = Instant.ofEpochMilli(dto.getStartAt()).atZone(ZoneOffset.UTC).toLocalDate();
		if (Math.abs(ChronoUnit.DAYS.between(startDayUtc, dto.getWorkDate())) > 1) {
			throw fail(ErrorCode.INVALID_PARAM, "归属日期与开始时间相差过大");
		}
	}

	/** 需求 5.6「分段不重叠且升序」「时长一致性」 */
	private void checkSegments(WorkRecordDTO dto) {
		List<WorkSegmentDTO> segments = dto.getSegments();
		if (segments.isEmpty()) {
			// 如果你的「手动补录」允许没有分段，把这个判断去掉，并跳过下面的时长一致性检查
			throw fail(ErrorCode.INVALID_PARAM, "至少需要一个分段");
		}
		if (segments.size() > MAX_SEGMENTS) {
			throw fail(ErrorCode.INVALID_PARAM, "分段数量超限");
		}

		long tolerance = bizProps.getTimer().getDurationToleranceMs();
		Set<String> ids = new HashSet<>();
		long prevEnd = dto.getStartAt();   // 第一个分段不能早于记录开始
		int prevNo = 0;
		long sumSpan = 0;

		for (WorkSegmentDTO seg : segments) {
			if (!ids.add(seg.getId())) {
				throw fail(ErrorCode.INVALID_PARAM, "分段 ID 重复");
			}
			if (seg.getSegmentNo() <= prevNo) {
				throw fail(ErrorCode.INVALID_PARAM, "分段序号必须从小到大");
			}
			prevNo = seg.getSegmentNo();

			if (seg.getEndAt() < seg.getStartAt()) {
				throw fail(ErrorCode.TIMER_TIME_INVALID, "分段结束时间早于开始时间");
			}
			if (seg.getStartAt() < prevEnd) {
				throw fail(ErrorCode.TIMER_TIME_INVALID, "分段重叠、未按时间升序，或早于记录开始时间");
			}
			long span = seg.getEndAt() - seg.getStartAt();
			if (Math.abs(seg.getDurationMs() - span) > tolerance) {
				throw fail(ErrorCode.TIMER_TIME_INVALID, "分段时长与起止时间不一致");
			}
			checkParts(seg);

			prevEnd = seg.getEndAt();
			sumSpan += span;
		}

		if (prevEnd > dto.getEndAt()) {
			throw fail(ErrorCode.TIMER_TIME_INVALID, "分段超出记录结束时间");
		}
		// 记录时长与各分段时间戳差之和，误差 ≤ 1 秒
		if (Math.abs(dto.getDurationMs() - sumSpan) > tolerance) {
			throw fail(ErrorCode.TIMER_TIME_INVALID, "记录时长与分段之和不一致");
		}
	}

	/** 计价片段：这几个字段在 DTO 上没有 @Min，这里补上 */
	private void checkParts(WorkSegmentDTO seg) {
		List<PricingPart> parts = seg.getParts();
		if (parts == null) {
			return;
		}
		if (parts.size() > MAX_PARTS_PER_SEGMENT) {
			throw fail(ErrorCode.INVALID_PARAM, "计价片段数量超限");
		}
		for (PricingPart p : parts) {
			if (p.getDurationMs() < 0 || p.getRateCentsPerHour() < 0 || p.getAmountCents() < 0) {
				throw fail(ErrorCode.INVALID_PARAM, "计价片段不能为负数");
			}
		}
	}

	/** 需求 5.6「金额区间」；5.5「记录总额 = 各片段金额之和」 */
	private void checkAmount(WorkRecordDTO dto) {
		long sumEarn = 0;
		for (WorkSegmentDTO seg : dto.getSegments()) {
			sumEarn += seg.getEarnCents();
		}
		if (sumEarn != dto.getTotalEarnCents()) {
			throw fail(ErrorCode.AMOUNT_OUT_OF_RANGE, "记录总额与分段金额之和不一致");
		}

		PayRule rule = dto.getRuleSnapshot();
		if (rule == null) {
			return;   // 没有计薪规则快照就无法算区间，只做上面的一致性检查
		}
		// 全部按最低费率算出下限，全部按最高费率算出上限；不强制精确重算，
		// 因为当天已累计的工时可能在别的设备上，服务端不一定看得到
		long[] bounds = rateBounds(rule);
		long lo = PricingUtil.amountCents(dto.getDurationMs(), clampToInt(bounds[0]));
		long hi = PricingUtil.amountCents(dto.getDurationMs(), clampToInt(bounds[1]));
		long slack = dto.getSegments().size() + 1;   // 每个片段四舍五入最多差半分
		if (dto.getTotalEarnCents() < lo - slack || dto.getTotalEarnCents() > hi + slack) {
			throw fail(ErrorCode.AMOUNT_OUT_OF_RANGE, "金额超出合理区间");
		}
	}

	private void checkTags(WorkRecordDTO dto) {
		if (dto.getConflictOf() != null && dto.getConflictOf().length() > 36) {
			throw fail(ErrorCode.INVALID_PARAM, "conflictOf 过长");
		}
		List<String> tags = dto.getTags();
		if (tags == null) {
			return;
		}
		if (tags.size() > MAX_TAGS) {
			throw fail(ErrorCode.INVALID_PARAM, "标签数量超限");
		}
		for (String tag : tags) {
			if (tag == null || tag.length() > MAX_TAG_LENGTH) {
				throw fail(ErrorCode.INVALID_PARAM, "标签不合法");
			}
		}
	}

	// ------------------------------------------------------------------
	// 设置
	// ------------------------------------------------------------------

	private SettingsDTO parseSettings(SyncChange change) {
		// 设置每个用户只有一条，entityId 固定是 "settings"
		if (!Constants.SETTINGS_ENTITY_ID.equals(change.getEntityId())) {
			throw fail(ErrorCode.INVALID_PARAM, "设置的 entityId 必须是 settings");
		}
		SettingsDTO dto = readPayload(change.getPayload(), SettingsDTO.class);
		checkAnnotations(dto);

		PayRule rule = dto.getPayRule();
		if (rule.getBaseRateCents() > MAX_BASE_RATE_CENTS) {
			throw fail(ErrorCode.AMOUNT_OUT_OF_RANGE, "基础时薪超出合理范围");
		}
		if (rule.getOvertimeThresholdMinutes() > MAX_MINUTES) {
			throw fail(ErrorCode.INVALID_PARAM, "加班阈值超出范围");
		}
		rateBounds(rule);   // 自定义费率格式不对会在这里被拒绝

		Integer reminder = dto.getTimerReminderMinutes();
		if (reminder != null && (reminder < 0 || reminder > MAX_MINUTES)) {
			throw fail(ErrorCode.INVALID_PARAM, "计时提醒分钟数超出范围");
		}
		if (dto.getCustomMotivations() != null) {
			if (dto.getCustomMotivations().size() > MAX_LIST_SIZE) {
				throw fail(ErrorCode.INVALID_PARAM, "自定义激励词数量超限");
			}
			for (String m : dto.getCustomMotivations()) {
				if (m == null || m.length() > MAX_MOTIVATION_LENGTH) {
					throw fail(ErrorCode.INVALID_PARAM, "自定义激励词不合法");
				}
			}
		}
		if ((dto.getToolsOrder() != null && dto.getToolsOrder().size() > MAX_LIST_SIZE)
				|| (dto.getToolsHidden() != null && dto.getToolsHidden().size() > MAX_LIST_SIZE)
				|| (dto.getFieldUpdatedAt() != null && dto.getFieldUpdatedAt().size() > MAX_LIST_SIZE)) {
			throw fail(ErrorCode.INVALID_PARAM, "列表长度超限");
		}
		return dto;
	}

	// ------------------------------------------------------------------
	// 费率区间（法定：基础×1.0 ~ ×3.0；自定义：基础和三档里最小/最大的）
	// ------------------------------------------------------------------

	/** @return {最低费率, 最高费率}，单位：分/小时 */
	private long[] rateBounds(PayRule rule) {
		long base = rule.getBaseRateCents();
		if (rule.getPayMode() == PayMode.STATUTORY) {
			return new long[]{base, base * 3};
		}
		long min = base;
		long max = base;
		CustomRates c = rule.getCustomRates();
		if (c != null) {
			for (RateInput in : new RateInput[]{c.getOvertime(), c.getRestDay(), c.getHoliday()}) {
				long r = resolveRate(in, base);
				min = Math.min(min, r);
				max = Math.max(max, r);
			}
		}
		return new long[]{min, max};
	}

	/** 留空或 0：用基础时薪；倍数：基础×倍数；单价：元/小时 ×100 换成分 */
	private long resolveRate(RateInput in, long base) {
		if (in == null || in.getInputType() == null || in.getInputType() == RateInputType.NONE
				|| in.getValue() == null || in.getValue().isBlank()) {
			return base;
		}
		try {
			BigDecimal v = new BigDecimal(in.getValue().trim());
			if (v.signum() <= 0) {
				return base;
			}
			BigDecimal cents = in.getInputType() == RateInputType.MULTIPLIER
					? v.multiply(BigDecimal.valueOf(base))
					: v.multiply(BigDecimal.valueOf(100));
			return cents.setScale(0, RoundingMode.HALF_UP).longValueExact();
		} catch (ArithmeticException | NumberFormatException e) {
			throw fail(ErrorCode.INVALID_PARAM, "自定义费率格式错误");
		}
	}

	private int clampToInt(long v) {
		return (int) Math.min(v, Integer.MAX_VALUE);
	}

	// ------------------------------------------------------------------
	// 通用：解析 payload、注解校验
	// ------------------------------------------------------------------

	/** 第 1 层：JSON → DTO。字段类型不对、枚举值不认识、日期格式错误等都会在这里被拒绝 */
	private <T> T readPayload(JsonNode payload, Class<T> type) {
		if (payload == null || payload.isNull() || !payload.isObject()) {
			throw fail(ErrorCode.INVALID_PARAM, "UPSERT 缺少 payload");
		}
		try {
			return objectMapper.treeToValue(payload, type);
		} catch (JsonProcessingException | IllegalArgumentException e) {
			log.warn("payload 解析失败 type={} cause={}", type.getSimpleName(), e.getMessage());
			throw fail(ErrorCode.INVALID_PARAM, "payload 格式错误");   // 不把异常细节返回给客户端
		}
	}

	/** 第 2 层：执行 DTO 上的 @NotNull / @Min / @Size 等注解，取排序后的第一条错误，保证结果稳定 */
	private void checkAnnotations(Object dto) {
		validator.validate(dto).stream()
				.map(v -> v.getPropertyPath() + " " + v.getMessage())
				.sorted()
				.findFirst()
				.ifPresent(msg -> {
					throw fail(ErrorCode.INVALID_PARAM, msg);
				});
	}

	private ChangeRejectedException fail(ErrorCode code, String message) {
		return new ChangeRejectedException(code, message);
	}
}
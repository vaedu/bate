package com.beat.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beat.pay.common.BizException;
import com.beat.pay.common.ErrorCode;
import com.beat.pay.common.PageResult;
import com.beat.pay.converter.WorkRecordConverter;
import com.beat.pay.dto.query.GoalQuery;
import com.beat.pay.dto.query.NotificationQuery;
import com.beat.pay.dto.query.PayrollQuery;
import com.beat.pay.dto.query.WorkRecordQuery;
import com.beat.pay.dto.record.GoalDTO;
import com.beat.pay.dto.record.NotificationDTO;
import com.beat.pay.dto.record.PayrollDTO;
import com.beat.pay.dto.record.ScheduleDTO;
import com.beat.pay.dto.record.SettingsDTO;
import com.beat.pay.dto.record.WorkRecordDTO;
import com.beat.pay.entity.BeatUserSettings;
import com.beat.pay.entity.BeatWorkRecord;
import com.beat.pay.entity.BeatWorkSegment;
import com.beat.pay.mapper.BeatUserSettingsMapper;
import com.beat.pay.mapper.BeatWorkRecordMapper;
import com.beat.pay.mapper.BeatWorkSegmentMapper;
import com.beat.pay.service.DataQueryService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cn.hutool.core.bean.BeanUtil;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class DataQueryServiceImpl implements DataQueryService {
	private final BeatUserSettingsMapper settingsMapper;
	private final BeatWorkRecordMapper workRecordMapper;
	private final BeatWorkSegmentMapper workSegmentMapper;

	@Override
	public SettingsDTO getSettings(Long userId) {
		BeatUserSettings settings =
				settingsMapper.selectOne(new LambdaQueryWrapper<BeatUserSettings>().eq(BeatUserSettings::getUserId,
						userId));
		return BeanUtil.copyProperties(settings, SettingsDTO.class);
	}

	/**
	 * 分页查询用户的工作记录列表。
	 * 支持按日期范围、标签、班次、关键字等条件筛选，支持自定义排序。
	 * 查询结果包含每条记录关联的工作时段（segments）信息。
	 *
	 * @param userId 用户 ID，用于数据权限隔离
	 * @param query  查询条件对象，包含分页参数、筛选条件和排序规则
	 * @return 分页结果对象，包含记录列表、当前页码、每页大小和总记录数
	 * @throws BizException 当日期范围参数不合法时抛出
	 */
	@Override
	public PageResult<WorkRecordDTO> listWorkRecords(Long userId, WorkRecordQuery query) {
		// 1. 校验日期范围参数（起始日期不晚于结束日期，且跨度不超过 366 天）
		validateDateRange(query.getDateFrom(), query.getDateTo());

		// 2. 构建基础查询条件：用户 ID、日期范围、是否包含已删除记录
		LambdaQueryWrapper<BeatWorkRecord> wrapper = new LambdaQueryWrapper<BeatWorkRecord>()
				// 2.1 限定用户 ID（数据权限隔离）
				.eq(BeatWorkRecord::getUserId, userId)
				// 2.2 大于等于起始日期（当起始日期不为空时生效）
				.ge(query.getDateFrom() != null, BeatWorkRecord::getWorkDate, query.getDateFrom())
				// 2.3 小于等于结束日期（当结束日期不为空时生效）
				.le(query.getDateTo() != null, BeatWorkRecord::getWorkDate, query.getDateTo())
				// 2.4 默认不查询已删除记录（除非 includeDeleted 显式为 true）
				.eq(!Boolean.TRUE.equals(query.getIncludeDeleted()), BeatWorkRecord::getDeleted, false);

		// 3. 处理标签筛选（tags 字段为 JSON 数组，支持多标签 OR 匹配）
		if (query.getTag() != null && !query.getTag().isEmpty()) {
			wrapper.and(w -> {
				// 遍历所有指定的标签，构造 OR 条件
				for (int i = 0; i < query.getTag().size(); i++) {
					String tag = query.getTag().get(i);
					if (i == 0) {
						// 第一个标签：使用 JSON_CONTAINS 判断 tags 数组是否包含该标签
						w.apply("JSON_CONTAINS(tags, JSON_ARRAY(0))", tag);
					} else {
						// 后续标签：使用 OR 连接
						w.or().apply("JSON_CONTAINS(tags, JSON_ARRAY(0))", tag);
					}
				}
			});
		}

		// 4. 处理全文关键字搜索（q 参数）
		if (query.getQ() != null && !query.getQ().isEmpty()) {
			// 4.1 转义 LIKE 通配符（防止用户输入 % 或 _ 造成查询范围过大）
			String like = "%" + escapeLike(query.getQ().trim()) + "%";
			// 4.2 在备注、标签、工作日期三个字段中进行模糊匹配（OR 关系）
			wrapper.and(w -> w.like(BeatWorkRecord::getNote, like)
					.or().apply("CAST(tags AS CHAR) LIKE {0}", "%" + like + "%")
					.or().apply("CAST(work_date AS CHAR) LIKE {0}", "%" + like + "%"));
		}

		// 5. 处理班次筛选（当 shift 参数不为空时生效）
		wrapper.eq(query.getShift() != null,
				BeatWorkRecord::getShift, query.getShift());

		// 6. 应用排序规则（根据 query.getSort() 解析排序字段和方向）
		applySort(wrapper, query.getSort());

		// 7. 添加 ID 升序作为次要排序（保证结果顺序稳定）
		wrapper.orderByAsc(BeatWorkRecord::getId);

		// 8. 执行分页查询（使用 MyBatis-Plus 的 Page 对象）
		Page<BeatWorkRecord> result = workRecordMapper.selectPage(
				Page.of(query.getPage(), query.getSize()), wrapper);

		// 9. 获取本页的工作记录列表
		List<BeatWorkRecord> records = result.getRecords();

		// 10. 批量查询关联的工作时段（segments），避免 N+1 查询问题
		Map<String, List<BeatWorkSegment>> segMap = records.isEmpty() ? Map.of()
				// 10.1 当记录列表不为空时，查询所有记录关联的 segments
				: workSegmentMapper.selectList(new LambdaQueryWrapper<BeatWorkSegment>()
						// 限定用户 ID（数据权限）
						.eq(BeatWorkSegment::getUserId, userId)
						// 限定记录 ID 范围（使用 IN 批量查询）
						.in(BeatWorkSegment::getRecordId, records.stream().map(BeatWorkRecord::getId).toList())
						// 按时段序号升序排列
						.orderByAsc(BeatWorkSegment::getSegmentNo))
				// 10.2 按 recordId 分组，构造 Map<recordId, List<segment>> 结构
				.stream().collect(Collectors.groupingBy(BeatWorkSegment::getRecordId));

		// 11. 组装 DTO 对象（将实体对象和关联的 segments 转换为前端需要的 DTO）
		List<WorkRecordDTO> items = records.stream()
				.map(r -> WorkRecordConverter.toDto(r, segMap.getOrDefault(r.getId(), List.of())))
				.toList();

		// 12. 返回分页结果（包含数据列表、当前页码、每页大小、总记录数）
		return PageResult.of(items, result.getCurrent(), result.getSize(), result.getTotal());
	}



	@Override
	public WorkRecordDTO getWorkRecord(Long userId, String recordId) {
		// TODO: API-DAT-03（需求：CAL-002,REC-002）
		throw new UnsupportedOperationException("TODO: API-DAT-03");
	}

	@Override
	public PageResult<GoalDTO> listGoals(Long userId, GoalQuery query) {
		// TODO: API-DAT-04（需求：GOL-001~004）
		throw new UnsupportedOperationException("TODO: API-DAT-04");
	}

	@Override
	public List<ScheduleDTO> listSchedules(Long userId, LocalDate dateFrom, LocalDate dateTo) {
		// TODO: API-DAT-05（需求：CAL-003）
		throw new UnsupportedOperationException("TODO: API-DAT-05");
	}

	@Override
	public PageResult<PayrollDTO> listPayrolls(Long userId, PayrollQuery query) {
		// TODO: API-DAT-06（需求：LDG-001~003）
		throw new UnsupportedOperationException("TODO: API-DAT-06");
	}

	@Override
	public PageResult<NotificationDTO> listNotifications(Long userId, NotificationQuery query) {
		// TODO: API-DAT-07（需求：NTF-001~003）
		throw new UnsupportedOperationException("TODO: API-DAT-07");
	}

	/**
	 * 校验日期范围参数的合法性。
	 * 当起始日期和结束日期均不为空时，要求起始日期不能晚于结束日期，
	 * 且日期跨度不能超过 366 天；任一日期为空时不进行范围校验。
	 *
	 * @param from 起始日期，可为空
	 * @param to   结束日期，可为空
	 * @throws BizException 当起始日期晚于结束日期，或日期范围超过 366 天时抛出
	 */
	private void validateDateRange(LocalDate from, LocalDate to) {
		if (from != null && to != null) {
			if (from.isAfter(to)) throw new BizException(ErrorCode.INVALID_PARAM, "dateFrom 不能晚于 dateTo");
			if (ChronoUnit.DAYS.between(from, to) > 366)
				throw new BizException(ErrorCode.INVALID_PARAM, "日期范围不能超过 366 天");
		}
	}

	/**
	 * 转义 LIKE 通配符，避免用户输入 % 或 _ 查出所有数据
	 */
	private String escapeLike(String s) {
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
	/**
	 * 应用排序规则到查询条件构造器。
	 * 排序参数格式为 "字段名,方向"，例如 "workDate,desc" 或 "totalEarnCents,asc"。
	 * 支持的排序字段包括：workDate、startAt、totalEarnCents、updatedAt。
	 *
	 * @param wrapper 查询条件构造器，用于添加排序规则
	 * @param sort    排序参数字符串，格式为 "字段名,asc|desc"，可为空
	 * @throws BizException 当排序参数格式错误或字段名不支持时抛出
	 */
	private void applySort(LambdaQueryWrapper<BeatWorkRecord> wrapper, String sort) {
		// 1. 未指定排序参数时，默认按工作日期降序排列（最新的记录在前）
		if (sort == null || sort.isBlank()) {
			wrapper.orderByDesc(BeatWorkRecord::getWorkDate);
			return;
		}

		// 2. 解析排序参数（格式：字段名,asc|desc）
		String[] parts = sort.split(",");
		if (parts.length != 2) {
			throw new BizException(ErrorCode.INVALID_PARAM, "排序参数格式错误，应为 字段,asc|desc");
		}

		// 3. 提取字段名和排序方向
		String field = parts[0];        // 排序字段名
		String direction = parts[1];    // 排序方向（asc 或 desc）

		// 4. 判断是否为升序排序（不区分大小写）
		boolean asc = "asc".equalsIgnoreCase(direction);

		// 5. 根据字段名应用相应的排序规则
		switch (field) {
			case "workDate" -> wrapper.orderBy(true, asc, BeatWorkRecord::getWorkDate);           // 按工作日期排序
			case "startAt" -> wrapper.orderBy(true, asc, BeatWorkRecord::getStartAt);             // 按开始时间排序
			case "totalEarnCents" -> wrapper.orderBy(true, asc, BeatWorkRecord::getTotalEarnCents); // 按总收入排序
			case "updatedAt" -> wrapper.orderBy(true, asc, BeatWorkRecord::getUpdatedAt);         // 按更新时间排序
			default -> throw new BizException(ErrorCode.INVALID_PARAM, "不支持的排序字段");          // 不支持的字段名
		}
	}
}

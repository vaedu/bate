package com.beat.pay.converter;

import com.beat.pay.dto.record.WorkRecordDTO;
import com.beat.pay.dto.record.WorkSegmentDTO;
import com.beat.pay.entity.BeatWorkRecord;
import com.beat.pay.entity.BeatWorkSegment;
import org.springframework.stereotype.Component;

import java.util.List;
@Component
public class WorkRecordConverter {
	/**
	 * 将工时记录实体及其分段实体列表转换为工时记录数据传输对象。
	 * 转换结果包含记录基础信息、同步相关信息、计薪规则快照以及由分段实体映射得到的分段数据。
	 *
	 * @param r 工时记录实体，不能为 null
	 * @param segs 工时分段实体列表，不能为 null
	 * @return 映射后的工时记录数据传输对象
	 */
	public static WorkRecordDTO toDto(BeatWorkRecord r, List<BeatWorkSegment> segs) {
		WorkRecordDTO d = new WorkRecordDTO();
		d.setId(r.getId());                 d.setVersion(r.getVersion());
		d.setUpdatedAt(r.getUpdatedAt());   d.setDeleted(r.getDeleted());
		d.setWorkDate(r.getWorkDate());     d.setStartAt(r.getStartAt());
		d.setEndAt(r.getEndAt());           d.setDurationMs(r.getDurationMs());
		d.setTotalEarnCents(r.getTotalEarnCents());
		d.setShift(r.getShift());           d.setTags(r.getTags());
		d.setNote(r.getNote());             d.setCrossDay(r.getCrossDay());
		d.setSource(r.getSource());         d.setConflictOf(r.getConflictOf());
		d.setRuleSnapshot(r.getRuleSnapshot());
		d.setSegments(segs.stream().map(s -> {
			WorkSegmentDTO x = new WorkSegmentDTO();
			x.setId(s.getId());             x.setSegmentNo(s.getSegmentNo());
			x.setStartAt(s.getStartAt());   x.setEndAt(s.getEndAt());
			x.setDurationMs(s.getDurationMs()); x.setEarnCents(s.getEarnCents());
			x.setParts(s.getParts());
			return x;
		}).toList());
		return d;
	}
}

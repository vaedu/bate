package com.beat.pay.converter;

import com.beat.pay.dto.record.WorkRecordDTO;
import com.beat.pay.dto.record.WorkSegmentDTO;
import com.beat.pay.entity.BeatWorkRecord;
import com.beat.pay.entity.BeatWorkSegment;
import org.springframework.stereotype.Component;

import java.util.List;
@Component
public class WorkRecordConverter {
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

package com.beat.pay.service;

import com.beat.pay.common.PageResult;
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
import java.time.LocalDate;
import java.util.List;

public interface DataQueryService {

    /** API-DAT-01（需求：SET-001,014） */
    SettingsDTO getSettings(Long userId);

    /** API-DAT-02（需求：REC-001~005） */
    PageResult<WorkRecordDTO> listWorkRecords(Long userId, WorkRecordQuery query);

    /** API-DAT-03（需求：CAL-002,REC-002） */
    WorkRecordDTO getWorkRecord(Long userId, String recordId);

    /** API-DAT-04（需求：GOL-001~004） */
    PageResult<GoalDTO> listGoals(Long userId, GoalQuery query);

    /** API-DAT-05（需求：CAL-003） */
    List<ScheduleDTO> listSchedules(Long userId, LocalDate dateFrom, LocalDate dateTo);

    /** API-DAT-06（需求：LDG-001~003） */
    PageResult<PayrollDTO> listPayrolls(Long userId, PayrollQuery query);

    /** API-DAT-07（需求：NTF-001~003） */
    PageResult<NotificationDTO> listNotifications(Long userId, NotificationQuery query);
}

package com.beat.pay.service.impl;

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
import com.beat.pay.service.DataQueryService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DataQueryServiceImpl implements DataQueryService {

    @Override
    public SettingsDTO getSettings(Long userId) {
        // TODO: API-DAT-01（需求：SET-001,014）
        throw new UnsupportedOperationException("TODO: API-DAT-01");
    }

    @Override
    public PageResult<WorkRecordDTO> listWorkRecords(Long userId, WorkRecordQuery query) {
        // TODO: API-DAT-02（需求：REC-001~005）
        throw new UnsupportedOperationException("TODO: API-DAT-02");
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
}

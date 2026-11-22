package com.beat.pay.controller;

import com.beat.pay.common.PageResult;
import com.beat.pay.common.Result;
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
import com.beat.pay.security.UserContext;
import com.beat.pay.service.DataQueryService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DataQueryController {

    private final DataQueryService dataQueryService;

    /** API-DAT-01（需求：SET-001,014） */
    @GetMapping("/settings")
    public Result<SettingsDTO> getSettings() {
        return Result.ok(dataQueryService.getSettings(UserContext.userId()));
    }

    /** API-DAT-02（需求：REC-001~005） */
    @GetMapping("/work-records")
    public Result<PageResult<WorkRecordDTO>> listWorkRecords(@Valid @ModelAttribute WorkRecordQuery query) {
        return Result.ok(dataQueryService.listWorkRecords(UserContext.userId(), query));
    }

    /** API-DAT-03（需求：CAL-002,REC-002） */
    @GetMapping("/work-records/{recordId}")
    public Result<WorkRecordDTO> getWorkRecord(@PathVariable String recordId) {
        return Result.ok(dataQueryService.getWorkRecord(UserContext.userId(), recordId));
    }

    /** API-DAT-04（需求：GOL-001~004） */
    @GetMapping("/goals")
    public Result<PageResult<GoalDTO>> listGoals(@Valid @ModelAttribute GoalQuery query) {
        return Result.ok(dataQueryService.listGoals(UserContext.userId(), query));
    }

    /** API-DAT-05（需求：CAL-003） */
    @GetMapping("/schedules")
    public Result<List<ScheduleDTO>> listSchedules(@RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo) {
        return Result.ok(dataQueryService.listSchedules(UserContext.userId(), dateFrom, dateTo));
    }

    /** API-DAT-06（需求：LDG-001~003） */
    @GetMapping("/payrolls")
    public Result<PageResult<PayrollDTO>> listPayrolls(@Valid @ModelAttribute PayrollQuery query) {
        return Result.ok(dataQueryService.listPayrolls(UserContext.userId(), query));
    }

    /** API-DAT-07（需求：NTF-001~003） */
    @GetMapping("/notifications")
    public Result<PageResult<NotificationDTO>> listNotifications(@Valid @ModelAttribute NotificationQuery query) {
        return Result.ok(dataQueryService.listNotifications(UserContext.userId(), query));
    }
}

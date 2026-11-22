package com.beat.pay.controller;

import com.beat.pay.common.Constants;
import com.beat.pay.common.Result;
import com.beat.pay.dto.timer.TimerActionRequest;
import com.beat.pay.dto.timer.TimerSessionVO;
import com.beat.pay.dto.timer.TimerStartRequest;
import com.beat.pay.dto.timer.TimerStopRequest;
import com.beat.pay.dto.timer.TimerStopResult;
import com.beat.pay.security.UserContext;
import com.beat.pay.service.TimerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TimerController {

    private final TimerService timerService;

    /** API-TMR-01（需求：TMR-013,016,017） */
    @GetMapping("/timer/sessions/current")
    public Result<TimerSessionVO> current() {
        return Result.ok(timerService.current(UserContext.userId()));
    }

    /** API-TMR-02（需求：TMR-013,014,018） */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/timer/sessions")
    public Result<TimerSessionVO> start(@RequestHeader(Constants.HEADER_IDEMPOTENCY_KEY) String idemKey, @Valid @RequestBody TimerStartRequest req) {
        return Result.ok(timerService.start(UserContext.userId(), idemKey, req));
    }

    /** API-TMR-03（需求：TMR-001,002,015,016） */
    @PostMapping("/timer/sessions/{sessionId}/pause")
    public Result<TimerSessionVO> pause(@RequestHeader(Constants.HEADER_IDEMPOTENCY_KEY) String idemKey, @PathVariable String sessionId, @Valid @RequestBody TimerActionRequest req) {
        return Result.ok(timerService.pause(UserContext.userId(), idemKey, sessionId, req));
    }

    /** API-TMR-04（需求：TMR-001,002,014,015） */
    @PostMapping("/timer/sessions/{sessionId}/resume")
    public Result<TimerSessionVO> resume(@RequestHeader(Constants.HEADER_IDEMPOTENCY_KEY) String idemKey, @PathVariable String sessionId, @Valid @RequestBody TimerActionRequest req) {
        return Result.ok(timerService.resume(UserContext.userId(), idemKey, sessionId, req));
    }

    /** API-TMR-05（需求：TMR-001,008,019,029） */
    @PostMapping("/timer/sessions/{sessionId}/stop")
    public Result<TimerStopResult> stop(@RequestHeader(Constants.HEADER_IDEMPOTENCY_KEY) String idemKey, @PathVariable String sessionId, @Valid @RequestBody TimerStopRequest req) {
        return Result.ok(timerService.stop(UserContext.userId(), idemKey, sessionId, req));
    }

    /** API-TMR-06（需求：TMR-001） */
    @PostMapping("/timer/sessions/{sessionId}/cancel")
    public Result<TimerSessionVO> cancel(@RequestHeader(Constants.HEADER_IDEMPOTENCY_KEY) String idemKey, @PathVariable String sessionId, @Valid @RequestBody TimerActionRequest req) {
        return Result.ok(timerService.cancel(UserContext.userId(), idemKey, sessionId, req));
    }
}

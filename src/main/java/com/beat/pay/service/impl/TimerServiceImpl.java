package com.beat.pay.service.impl;

import com.beat.pay.dto.timer.TimerActionRequest;
import com.beat.pay.dto.timer.TimerSessionVO;
import com.beat.pay.dto.timer.TimerStartRequest;
import com.beat.pay.dto.timer.TimerStopRequest;
import com.beat.pay.dto.timer.TimerStopResult;
import com.beat.pay.service.TimerService;
import org.springframework.stereotype.Service;

@Service
public class TimerServiceImpl implements TimerService {

    @Override
    public TimerSessionVO current(Long userId) {
        // TODO: API-TMR-01（需求：TMR-013,016,017）
        throw new UnsupportedOperationException("TODO: API-TMR-01");
    }

    @Override
    public TimerSessionVO start(Long userId, String idemKey, TimerStartRequest req) {
        // TODO: API-TMR-02（需求：TMR-013,014,018）
        throw new UnsupportedOperationException("TODO: API-TMR-02");
    }

    @Override
    public TimerSessionVO pause(Long userId, String idemKey, String sessionId, TimerActionRequest req) {
        // TODO: API-TMR-03（需求：TMR-001,002,015,016）
        throw new UnsupportedOperationException("TODO: API-TMR-03");
    }

    @Override
    public TimerSessionVO resume(Long userId, String idemKey, String sessionId, TimerActionRequest req) {
        // TODO: API-TMR-04（需求：TMR-001,002,014,015）
        throw new UnsupportedOperationException("TODO: API-TMR-04");
    }

    @Override
    public TimerStopResult stop(Long userId, String idemKey, String sessionId, TimerStopRequest req) {
        // TODO: API-TMR-05（需求：TMR-001,008,019,029）
        throw new UnsupportedOperationException("TODO: API-TMR-05");
    }

    @Override
    public TimerSessionVO cancel(Long userId, String idemKey, String sessionId, TimerActionRequest req) {
        // TODO: API-TMR-06（需求：TMR-001）
        throw new UnsupportedOperationException("TODO: API-TMR-06");
    }
}

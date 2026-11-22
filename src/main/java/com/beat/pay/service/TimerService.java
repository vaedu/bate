package com.beat.pay.service;

import com.beat.pay.dto.timer.TimerActionRequest;
import com.beat.pay.dto.timer.TimerSessionVO;
import com.beat.pay.dto.timer.TimerStartRequest;
import com.beat.pay.dto.timer.TimerStopRequest;
import com.beat.pay.dto.timer.TimerStopResult;

public interface TimerService {

    /** API-TMR-01（需求：TMR-013,016,017） */
    TimerSessionVO current(Long userId);

    /** API-TMR-02（需求：TMR-013,014,018） */
    TimerSessionVO start(Long userId, String idemKey, TimerStartRequest req);

    /** API-TMR-03（需求：TMR-001,002,015,016） */
    TimerSessionVO pause(Long userId, String idemKey, String sessionId, TimerActionRequest req);

    /** API-TMR-04（需求：TMR-001,002,014,015） */
    TimerSessionVO resume(Long userId, String idemKey, String sessionId, TimerActionRequest req);

    /** API-TMR-05（需求：TMR-001,008,019,029） */
    TimerStopResult stop(Long userId, String idemKey, String sessionId, TimerStopRequest req);

    /** API-TMR-06（需求：TMR-001） */
    TimerSessionVO cancel(Long userId, String idemKey, String sessionId, TimerActionRequest req);
}

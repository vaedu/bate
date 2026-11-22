package com.beat.pay.enums;

/** 计时会话状态机：RUNNING <-> PAUSED -> STOPPED / CANCELED */
public enum TimerStatus {
    RUNNING, PAUSED, STOPPED, CANCELED;

    /** 活动会话 = 运行中或暂停中（占用全局互斥） */
    public boolean isActive() {
        return this == RUNNING || this == PAUSED;
    }
}

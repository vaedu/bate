package com.beat.pay.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 清理：超过保留期的墓碑（deleted=1 且 updated_at<now-90天）、对应的 beat_sync_change，以及过期的 beat_sync_receipt / 迁移任务 */
@Slf4j
@Component
public class SyncCleanupJob {

    @Scheduled(cron = "0 30 3 * * *")
    public void run() {
        // TODO: 清理后，旧游标的客户端 Pull 将得到 fullSyncRequired=true
        log.debug("SyncCleanupJob tick");
    }
}

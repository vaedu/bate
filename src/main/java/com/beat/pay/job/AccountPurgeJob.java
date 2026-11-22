package com.beat.pay.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 注销清理：冷静期结束（deletion_scheduled_at 到期）的账号，物理删除其全部云端业务数据；须在申请后 15 个工作日内完成 */
@Slf4j
@Component
public class AccountPurgeJob {

    @Scheduled(cron = "0 0 3 * * *")
    public void run() {
        // TODO: 查询 sys_user.status=PENDING_DELETION 且 deletion_scheduled_at<=now 的用户，分表分批删除后再删用户
        log.debug("AccountPurgeJob tick");
    }
}

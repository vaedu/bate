package com.beat.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 业务规则参数（来自需求文档 10.17 定稿清单） */
@Data
@ConfigurationProperties(prefix = "beat.biz")
public class BizProperties {

    private Sync sync = new Sync();
    private Account account = new Account();
    private Timer timer = new Timer();
    private Migration migration = new Migration();
    private Idempotency idempotency = new Idempotency();
    private Consent consent = new Consent();

    @Data
    public static class Sync {
        private int maxChangesPerPush = 100;
        private int pullDefaultLimit = 200;
        private int pullMaxLimit = 500;
        /** 墓碑保留天数 */
        private int tombstoneRetentionDays = 90;
    }

    @Data
    public static class Account {
        /** 注销冷静期（自然日） */
        private int deletionCoolingDays = 7;
        /** 平台硬性上限：注销处理时限（工作日） */
        private int deletionDeadlineWorkingDays = 15;
    }

    @Data
    public static class Timer {
        /** 单条记录最大跨度（小时） */
        private int maxRecordSpanHours = 48;
        /** 允许领先服务端时间的容差（分钟） */
        private int futureToleranceMinutes = 5;
        /** 时长一致性容差（毫秒） */
        private int durationToleranceMs = 1000;
    }

    @Data
    public static class Migration {
        private int batchMaxItems = 200;
        private int taskTtlDays = 7;
    }

    @Data
    public static class Consent {
        /** 云同步同意对应的当前隐私政策版本；升级后用户需重新同意 */
        private String currentPolicyVersion = "1.0";
    }

    @Data
    public static class Idempotency {
        /** 幂等记录保留天数（≥7） */
        private int retentionDays = 7;
    }
}

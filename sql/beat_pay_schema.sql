-- =====================================================================
-- Beat计薪 后端建库建表脚本（MySQL 8.0+，InnoDB，utf8mb4）
-- 对应：需求规格说明书 V1.0 / 接口设计文档 V1.0（本期，共 15 张表）
-- 约定：
--   * 所有时间点字段为毫秒级 Unix 时间戳（UTC，BIGINT）；日期字段为 DATE（客户端按设备时区得出）
--   * 金额/费率为整数“分”（BIGINT/INT），时长为毫秒
--   * 业务记录主键为客户端生成的 UUID（CHAR(36)）；所有查询必须带 user_id 条件
--   * 可同步实体统一含 version / deleted(软删除墓碑) / client_updated_at / origin_device_id
--   * 与接口文档 8.1/8.2 的差异见各表注释（beat_payroll 唯一键、beat_sync_receipt 增加 receipt_type）
-- =====================================================================

CREATE DATABASE IF NOT EXISTS beat_pay
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE beat_pay;

-- 建议：为新服务创建独立库账号（与 ATM-Web 等其他项目隔离），密码请自行替换
-- CREATE USER 'beat_app'@'%' IDENTIFIED BY '<请替换为强密码>';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON beat_pay.* TO 'beat_app'@'%';

-- ---------------------------------------------------------------------
-- 1. 用户
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
  id                     BIGINT       NOT NULL COMMENT '用户ID（雪花ID，接口以字符串返回）',
  nickname               VARCHAR(64)  NULL     COMMENT '昵称（可空，本期功能不依赖）',
  avatar_url             VARCHAR(512) NULL     COMMENT '头像地址（可空，有效期未知，仅展示）',
  status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PENDING_DELETION/FROZEN',
  deletion_requested_at  BIGINT       NULL     COMMENT '注销申请时间',
  deletion_scheduled_at  BIGINT       NULL     COMMENT '计划物理删除时间（冷静期结束，且不晚于申请后15个工作日）',
  last_login_at          BIGINT       NULL,
  sync_consent_at        BIGINT       NULL     COMMENT '云同步单独同意时间（PRV-004，合规留痕）；撤回同意后置空',
  sync_consent_version   VARCHAR(16)  NULL     COMMENT '同意时的隐私政策版本',
  created_at             BIGINT       NOT NULL,
  updated_at             BIGINT       NOT NULL,
  PRIMARY KEY (id),
  KEY idx_status_deletion (status, deletion_scheduled_at)
) ENGINE=InnoDB COMMENT='应用用户';

CREATE TABLE IF NOT EXISTS sys_user_identity (
  id            BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  provider      VARCHAR(20)  NOT NULL DEFAULT 'HUAWEI',
  union_id      VARCHAR(128) NOT NULL COMMENT '账号唯一键（UnionID）',
  open_id       VARCHAR(128) NULL     COMMENT '辅助（OpenID 随系统大版本/包名/签名变化，不能当唯一键）',
  last_login_at BIGINT       NULL,
  created_at    BIGINT       NOT NULL,
  updated_at    BIGINT       NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider_union (provider, union_id),
  KEY idx_provider_open (provider, open_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='华为账号身份映射';

CREATE TABLE IF NOT EXISTS sys_user_session (
  id                          BIGINT       NOT NULL COMMENT '会话ID（写入 Access Token 的 sid）',
  user_id                     BIGINT       NOT NULL,
  device_id                   VARCHAR(64)  NOT NULL,
  refresh_token_hash          CHAR(64)     NOT NULL COMMENT 'Refresh Token 的 SHA-256 十六进制摘要，不存明文',
  previous_refresh_token_hash CHAR(64)     NULL     COMMENT '上一枚（已轮换）摘要，用于重放检测',
  status                      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED',
  login_type                  VARCHAR(16)  NULL     COMMENT 'INTERACTIVE/SILENT',
  refresh_expires_at          BIGINT       NOT NULL,
  last_refresh_at             BIGINT       NULL,
  revoked_at                  BIGINT       NULL,
  revoke_reason               VARCHAR(64)  NULL     COMMENT 'LOGOUT/LOGOUT_ALL/DEVICE_REMOVED/REPLAY/ACCOUNT_DELETION',
  created_at                  BIGINT       NOT NULL,
  updated_at                  BIGINT       NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_device (user_id, device_id) COMMENT '同一设备只保留一条会话，重新登录时更新',
  UNIQUE KEY uk_refresh_hash (refresh_token_hash),
  KEY idx_prev_hash (previous_refresh_token_hash)
) ENGINE=InnoDB COMMENT='登录会话（设备级）';

CREATE TABLE IF NOT EXISTS sys_user_device (
  id             BIGINT       NOT NULL,
  user_id        BIGINT       NOT NULL,
  device_id      VARCHAR(64)  NOT NULL COMMENT '客户端生成并持久化的UUID，非硬件序列号',
  device_name    VARCHAR(64)  NULL,
  device_model   VARCHAR(64)  NULL,
  os_version     VARCHAR(32)  NULL,
  app_version    VARCHAR(32)  NULL,
  device_type    VARCHAR(16)  NULL COMMENT 'PHONE/TABLET/OTHER',
  status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED',
  last_active_at BIGINT       NULL,
  last_sync_at   BIGINT       NULL,
  created_at     BIGINT       NOT NULL,
  updated_at     BIGINT       NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_device (user_id, device_id)
) ENGINE=InnoDB COMMENT='用户设备';

-- ---------------------------------------------------------------------
-- 2. 业务数据（可同步实体）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS beat_user_settings (
  user_id                    BIGINT      NOT NULL,
  version                    INT         NOT NULL DEFAULT 1,
  pay_mode                   VARCHAR(16) NOT NULL DEFAULT 'STATUTORY' COMMENT 'STATUTORY/CUSTOM',
  base_rate_cents            INT         NOT NULL DEFAULT 0 COMMENT '基础时薪，分/小时',
  overtime_threshold_minutes INT         NOT NULL DEFAULT 480,
  custom_rates               JSON        NULL COMMENT '{overtime,restDay,holiday: RateInput}',
  switches                   JSON        NULL COMMENT '功能开关',
  timer_reminder_minutes     INT         NOT NULL DEFAULT 0 COMMENT '0 关闭',
  motivation_source          VARCHAR(16) NOT NULL DEFAULT 'LOCAL',
  custom_motivations         JSON        NULL,
  theme                      JSON        NULL,
  onboarded                  TINYINT(1)  NOT NULL DEFAULT 0,
  tools_order                JSON        NULL,
  tools_hidden               JSON        NULL,
  field_updated_at           JSON        NULL COMMENT '字段级最后修改时间，用于字段级合并',
  client_updated_at          BIGINT      NULL,
  origin_device_id           VARCHAR(64) NULL,
  created_at                 BIGINT      NOT NULL,
  updated_at                 BIGINT      NOT NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB COMMENT='用户设置（单例）；深色模式/悬浮窗位置等设备本地设置不入库';

CREATE TABLE IF NOT EXISTS beat_work_record (
  id                CHAR(36)    NOT NULL COMMENT '客户端生成UUID',
  user_id           BIGINT      NOT NULL,
  version           INT         NOT NULL DEFAULT 1,
  deleted           TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '软删除墓碑',
  client_updated_at BIGINT      NULL,
  origin_device_id  VARCHAR(64) NULL,
  work_date         DATE        NOT NULL COMMENT '归属日期（跨天记录归属开始日期）',
  start_at          BIGINT      NOT NULL,
  end_at            BIGINT      NOT NULL,
  duration_ms       BIGINT      NOT NULL,
  total_earn_cents  BIGINT      NOT NULL,
  shift             VARCHAR(10) NULL COMMENT 'MORNING/MIDDLE/NIGHT/REST',
  tags              JSON        NULL,
  note              VARCHAR(1000) NULL,
  cross_day         TINYINT(1)  NOT NULL DEFAULT 0,
  source            VARCHAR(16) NOT NULL DEFAULT 'TIMER',
  conflict_of       CHAR(36)    NULL COMMENT '冲突副本指向的原记录ID',
  rule_snapshot     JSON        NULL COMMENT '记录生成时的计薪规则快照',
  created_at        BIGINT      NOT NULL,
  updated_at        BIGINT      NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user_date (user_id, work_date),
  KEY idx_user_updated (user_id, updated_at),
  KEY idx_user_deleted_date (user_id, deleted, work_date)
) ENGINE=InnoDB COMMENT='工时记录';

CREATE TABLE IF NOT EXISTS beat_work_segment (
  id          CHAR(36)   NOT NULL,
  user_id     BIGINT     NOT NULL,
  record_id   CHAR(36)   NOT NULL,
  segment_no  INT        NOT NULL,
  start_at    BIGINT     NOT NULL,
  end_at      BIGINT     NOT NULL,
  duration_ms BIGINT     NOT NULL,
  earn_cents  BIGINT     NOT NULL DEFAULT 0,
  parts       JSON       NULL COMMENT '计价片段 [{rateType,durationMs,rateCentsPerHour,amountCents}]',
  created_at  BIGINT     NOT NULL,
  updated_at  BIGINT     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user_record (user_id, record_id)
) ENGINE=InnoDB COMMENT='工时分段（随记录整体读写，更新时先删后插）';

CREATE TABLE IF NOT EXISTS beat_timer_session (
  id                      CHAR(36)    NOT NULL COMMENT '会话ID（客户端生成UUID，重复提交幂等）',
  user_id                 BIGINT      NOT NULL,
  status                  VARCHAR(10) NOT NULL COMMENT 'RUNNING/PAUSED/STOPPED/CANCELED',
  work_date               DATE        NOT NULL,
  started_at              BIGINT      NOT NULL,
  ended_at                BIGINT      NULL,
  segments                JSON        NOT NULL COMMENT '[{segmentNo,startAt,endAt}]',
  start_device_id         VARCHAR(64) NOT NULL COMMENT '会话所属设备：只有它能暂停/继续',
  start_device_name       VARCHAR(64) NULL,
  last_operator_device_id VARCHAR(64) NULL,
  rule_snapshot           JSON        NULL,
  version                 INT         NOT NULL DEFAULT 1,
  record_id               CHAR(36)    NULL COMMENT '结束后生成的工时记录ID',
  -- 互斥核心：同一账号至多一个活动会话（NULL 不参与唯一性）。该列为生成列，实体不映射、不可写入
  active_flag             TINYINT GENERATED ALWAYS AS (IF(status IN ('RUNNING','PAUSED'), 1, NULL)) STORED,
  created_at              BIGINT      NOT NULL,
  updated_at              BIGINT      NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_active (user_id, active_flag),
  KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB COMMENT='计时会话（互斥由唯一索引保证）';

CREATE TABLE IF NOT EXISTS beat_goal (
  id                  CHAR(36)     NOT NULL,
  user_id             BIGINT       NOT NULL,
  version             INT          NOT NULL DEFAULT 1,
  deleted             TINYINT(1)   NOT NULL DEFAULT 0,
  client_updated_at   BIGINT       NULL,
  origin_device_id    VARCHAR(64)  NULL,
  name                VARCHAR(100) NOT NULL,
  target_amount_cents BIGINT       NOT NULL,
  target_date         DATE         NULL,
  reward_text         VARCHAR(200) NULL,
  achieved_at         BIGINT       NULL COMMENT '首次达成时间；达成通知据此去重',
  created_at          BIGINT       NOT NULL,
  updated_at          BIGINT       NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user_deleted (user_id, deleted)
) ENGINE=InnoDB COMMENT='存钱目标（进度由客户端计算）';

CREATE TABLE IF NOT EXISTS beat_schedule (
  id                BIGINT      NOT NULL COMMENT '内部ID；同步实体ID为日期 YYYY-MM-DD',
  user_id           BIGINT      NOT NULL,
  version           INT         NOT NULL DEFAULT 1,
  deleted           TINYINT(1)  NOT NULL DEFAULT 0,
  client_updated_at BIGINT      NULL,
  origin_device_id  VARCHAR(64) NULL,
  schedule_date     DATE        NOT NULL,
  shift             VARCHAR(10) NOT NULL,
  created_at        BIGINT      NOT NULL,
  updated_at        BIGINT      NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_date (user_id, schedule_date)
) ENGINE=InnoDB COMMENT='排班（以日期为自然键）';

CREATE TABLE IF NOT EXISTS beat_payroll (
  id                CHAR(36)     NOT NULL,
  user_id           BIGINT       NOT NULL,
  version           INT          NOT NULL DEFAULT 1,
  deleted           TINYINT(1)   NOT NULL DEFAULT 0,
  client_updated_at BIGINT       NULL,
  origin_device_id  VARCHAR(64)  NULL,
  pay_month         CHAR(7)      NOT NULL COMMENT 'YYYY-MM',
  pay_date          DATE         NULL,
  gross_cents       BIGINT       NOT NULL,
  net_cents         BIGINT       NOT NULL,
  received          TINYINT(1)   NOT NULL DEFAULT 0,
  note              VARCHAR(200) NULL,
  created_at        BIGINT       NOT NULL,
  updated_at        BIGINT       NOT NULL,
  PRIMARY KEY (id),
  -- 差异说明：接口文档 8.1 写“唯一键 user_id+pay_month”，但软删除后再新增同月份会冲突，故只建普通索引，
  -- “同月份去重”由应用层按需求 5.8 的去重规则保证
  KEY idx_user_month (user_id, pay_month)
) ENGINE=InnoDB COMMENT='薪资台账';

CREATE TABLE IF NOT EXISTS beat_notification (
  id                CHAR(36)     NOT NULL,
  user_id           BIGINT       NOT NULL,
  version           INT          NOT NULL DEFAULT 1,
  deleted           TINYINT(1)   NOT NULL DEFAULT 0,
  client_updated_at BIGINT       NULL,
  origin_device_id  VARCHAR(64)  NULL,
  type              VARCHAR(32)  NOT NULL COMMENT 'GOAL_ACHIEVED',
  title             VARCHAR(100) NOT NULL,
  content           VARCHAR(500) NULL,
  goal_id           CHAR(36)     NULL COMMENT '目标达成通知按 goal_id 去重',
  is_read           TINYINT(1)   NOT NULL DEFAULT 0,
  occurred_at       BIGINT       NULL COMMENT '通知产生时间（接口 createdAt）',
  created_at        BIGINT       NOT NULL,
  updated_at        BIGINT       NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user_read (user_id, is_read),
  KEY idx_user_goal (user_id, goal_id)
) ENGINE=InnoDB COMMENT='通知';

-- ---------------------------------------------------------------------
-- 3. 同步 / 幂等 / 迁移
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS beat_sync_change (
  id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增序号即同步游标',
  user_id          BIGINT      NOT NULL,
  entity_type      VARCHAR(20) NOT NULL,
  entity_id        VARCHAR(64) NOT NULL,
  op               VARCHAR(8)  NOT NULL COMMENT 'UPSERT/DELETE',
  version          INT         NOT NULL,
  origin_device_id VARCHAR(64) NULL,
  payload          JSON        NULL COMMENT 'UPSERT 为完整实体快照；DELETE 为空',
  created_at       BIGINT      NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id, id),
  KEY idx_user_entity (user_id, entity_type, entity_id)
) ENGINE=InnoDB COMMENT='同步变更日志（Pull 的数据来源）。注意：并发提交可能乱序可见，Push 需对同一用户串行化';

CREATE TABLE IF NOT EXISTS beat_sync_receipt (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       NOT NULL,
  receipt_type  VARCHAR(20)  NOT NULL COMMENT 'REQUEST（请求级）/CHANGE（变更级changeId）/MIGRATION_BATCH',
  idem_key      VARCHAR(128) NOT NULL COMMENT '幂等键；迁移批次为 migrationId:batchNo',
  request_hash  CHAR(64)     NULL     COMMENT '请求摘要，用于识别“同键不同内容”(10004)',
  response_body MEDIUMTEXT   NULL     COMMENT '首次响应，重复提交直接返回',
  http_status   SMALLINT     NULL,
  expires_at    BIGINT       NOT NULL COMMENT '保留 ≥7 天，定时清理',
  created_at    BIGINT       NOT NULL,
  PRIMARY KEY (id),
  -- 差异说明：接口文档为 UNIQUE(user_id, idem_key)，这里加入 receipt_type 以便同表承载变更级幂等
  UNIQUE KEY uk_user_type_key (user_id, receipt_type, idem_key),
  KEY idx_expires (expires_at)
) ENGINE=InnoDB COMMENT='请求/变更/迁移批次幂等记录';

CREATE TABLE IF NOT EXISTS beat_migration_task (
  migration_id        CHAR(36)     NOT NULL COMMENT '客户端生成',
  user_id             BIGINT       NOT NULL,
  device_id           VARCHAR(64)  NULL,
  app_version         VARCHAR(32)  NULL,
  schema_version      INT          NULL,
  status              VARCHAR(16)  NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/UPLOADING/COMPLETED/FAILED',
  expected_counts     JSON         NULL,
  received_counts     JSON         NULL,
  accepted_count      INT          NOT NULL DEFAULT 0,
  deduped_count       INT          NOT NULL DEFAULT 0,
  conflict_copy_count INT          NOT NULL DEFAULT 0,
  last_batch_no       INT          NOT NULL DEFAULT 0,
  failure_reason      VARCHAR(500) NULL,
  expires_at          BIGINT       NOT NULL COMMENT '默认创建后7天',
  created_at          BIGINT       NOT NULL,
  updated_at          BIGINT       NOT NULL,
  PRIMARY KEY (migration_id),
  KEY idx_user_status (user_id, status)
) ENGINE=InnoDB COMMENT='游客数据迁移任务；同一用户至多一个未完成任务由应用层校验';

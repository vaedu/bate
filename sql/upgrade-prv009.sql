-- PRV-009：已部署的库升级脚本（全新建库请直接用 schema.sql）
USE beat_pay;
ALTER TABLE sys_user
  ADD COLUMN sync_consent_revoked_at BIGINT NULL COMMENT '最近一次撤回云同步同意的时间（审计）' AFTER sync_consent_version;

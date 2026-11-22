-- =====================================================================
-- 二批预留表（本期不要执行）
-- =====================================================================
USE beat_pay;

-- 对应接口 API-CFG-01（节假日兜底/下发）；本期使用 App 内置 JSON，不需要此表
CREATE TABLE IF NOT EXISTS beat_holiday (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  year         SMALLINT     NOT NULL,
  holiday_date DATE         NOT NULL,
  type         VARCHAR(20)  NOT NULL COMMENT 'HOLIDAY / MAKEUP_WORKDAY',
  name         VARCHAR(50)  NULL,
  data_version VARCHAR(32)  NOT NULL,
  created_at   BIGINT       NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_date (holiday_date),
  KEY idx_year (year)
) ENGINE=InnoDB COMMENT='节假日与调休（二批）';

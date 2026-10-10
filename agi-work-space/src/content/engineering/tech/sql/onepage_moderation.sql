-- =============================================================================
-- 库：onepage_moderation（P5 举报）
-- 执行：USE onepage_moderation;
-- =============================================================================

CREATE TABLE IF NOT EXISTS report (
  id BIGINT PRIMARY KEY COMMENT '举报 ID',
  reporter_id BIGINT NOT NULL COMMENT '举报人',
  target_type VARCHAR(16) NOT NULL COMMENT 'post / user',
  target_id BIGINT NOT NULL COMMENT '被举报对象 ID',
  reason_code VARCHAR(32) NOT NULL COMMENT '原因枚举，产品定码表',
  detail VARCHAR(1000) NULL COMMENT '补充说明',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待处理 1 已处理',
  created_at DATETIME(3) NOT NULL COMMENT '举报时间',
  KEY idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报';

-- onepage_moderation：举报工单。已有库必须执行，否则运营 /ops 失败。可重复执行。

CREATE DATABASE IF NOT EXISTS onepage_moderation DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE onepage_moderation;

CREATE TABLE IF NOT EXISTS report (
  id BIGINT NOT NULL COMMENT '工单 ID',
  reporter_id BIGINT NOT NULL COMMENT '举报人 userId',
  target_type VARCHAR(16) NOT NULL COMMENT 'post / user',
  target_id BIGINT NOT NULL COMMENT '被举报对象 ID',
  reason_code VARCHAR(32) NOT NULL COMMENT '原因码',
  detail VARCHAR(1000) NULL COMMENT '补充说明',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待处理 1 已处理',
  handler_id BIGINT NULL COMMENT '处理人 userId',
  handle_action VARCHAR(16) NULL COMMENT 'dismiss / takedown / ban',
  handle_note VARCHAR(500) NULL COMMENT '处理备注',
  handled_at DATETIME(3) NULL COMMENT '处理时间',
  created_at DATETIME(3) NOT NULL COMMENT '提交时间',
  PRIMARY KEY (id),
  KEY idx_status_created (status, created_at, id),
  KEY idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报工单';

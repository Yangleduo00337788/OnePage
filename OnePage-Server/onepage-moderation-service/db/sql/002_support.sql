-- 帮助反馈 / 主播入驻 / 大 V 申请。已有库必须执行。可重复执行。
USE onepage_moderation;

CREATE TABLE IF NOT EXISTS support_ticket (
  id BIGINT NOT NULL COMMENT '工单 ID',
  user_id BIGINT NOT NULL COMMENT '提交人 userId',
  kind VARCHAR(16) NOT NULL COMMENT 'feedback / live / verify',
  title VARCHAR(64) NOT NULL DEFAULT '' COMMENT '申请名称；反馈可空',
  contact VARCHAR(64) NOT NULL DEFAULT '' COMMENT '联系方式',
  body VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '正文或补充说明',
  media_urls VARCHAR(4000) NOT NULL DEFAULT '[]' COMMENT '反馈附图公开 URL JSON 数组',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待处理 1 已处理',
  handler_id BIGINT NULL COMMENT '处理人 userId',
  handle_note VARCHAR(500) NULL COMMENT '处理备注',
  handled_at DATETIME(3) NULL COMMENT '处理时间',
  created_at DATETIME(3) NOT NULL COMMENT '提交时间',
  PRIMARY KEY (id),
  KEY idx_kind_status_created (kind, status, created_at, id),
  KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帮助反馈与入驻认证申请';

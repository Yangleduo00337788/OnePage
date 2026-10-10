-- =============================================================================
-- Outbox 模板（各需要发 RocketMQ 的库各建一张同名表）
-- content / social / user 已在对应库脚本里带上；本文件方便单独复制。
-- =============================================================================

CREATE TABLE IF NOT EXISTS outbox_event (
  id BIGINT PRIMARY KEY COMMENT '事件 ID，作 MQ 幂等键 eventId',
  aggregate_type VARCHAR(32) NOT NULL COMMENT '如 post、follow',
  aggregate_id VARCHAR(64) NOT NULL COMMENT '聚合根 ID',
  event_type VARCHAR(64) NOT NULL COMMENT '如 post.published',
  payload_json JSON NOT NULL COMMENT '事件正文',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待发送 1 已发送 2 失败',
  created_at DATETIME(3) NOT NULL COMMENT '写入时间（与业务同一事务）',
  sent_at DATETIME(3) NULL COMMENT '投递成功时间',
  KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事务消息 Outbox';

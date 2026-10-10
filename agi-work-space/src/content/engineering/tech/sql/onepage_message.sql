-- =============================================================================
-- 库：onepage_message（P2 私信）
-- 执行：USE onepage_message;
-- =============================================================================

CREATE TABLE IF NOT EXISTS conversation (
  id BIGINT PRIMARY KEY COMMENT '会话 ID',
  type VARCHAR(16) NOT NULL COMMENT 'MVP 仅 single 单聊',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '最后一条消息时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话';

CREATE TABLE IF NOT EXISTS conversation_member (
  conversation_id BIGINT NOT NULL COMMENT '会话 ID',
  user_id BIGINT NOT NULL COMMENT '成员',
  last_read_message_id BIGINT NULL COMMENT '已读游标',
  joined_at DATETIME(3) NOT NULL COMMENT '加入时间',
  PRIMARY KEY (conversation_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话成员';

CREATE TABLE IF NOT EXISTS message (
  id BIGINT PRIMARY KEY COMMENT '消息 ID，会话内递增语义靠 id 排序',
  conversation_id BIGINT NOT NULL COMMENT '会话 ID',
  sender_id BIGINT NOT NULL COMMENT '发送人',
  msg_type VARCHAR(16) NOT NULL COMMENT 'text / image / post_card / live_card',
  content JSON NOT NULL COMMENT '文本或卡片 payload',
  created_at DATETIME(3) NOT NULL COMMENT '发送时间',
  KEY idx_conv_id (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信消息';

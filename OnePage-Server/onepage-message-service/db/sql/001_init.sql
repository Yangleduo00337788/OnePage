-- onepage_message：单聊会话与消息。已有环境必须建库并执行。可重复执行。

CREATE DATABASE IF NOT EXISTS onepage_message DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE onepage_message;

CREATE TABLE IF NOT EXISTS conversation (
  id BIGINT NOT NULL COMMENT '会话 ID',
  type VARCHAR(16) NOT NULL COMMENT 'single',
  pair_key VARCHAR(64) NULL COMMENT '单聊双方 userId 升序拼接，防重复开聊',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '最后消息时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_pair (pair_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话';

CREATE TABLE IF NOT EXISTS conversation_member (
  conversation_id BIGINT NOT NULL COMMENT '会话 ID',
  user_id BIGINT NOT NULL COMMENT '成员',
  last_read_message_id BIGINT NULL COMMENT '已读到的消息 ID',
  joined_at DATETIME(3) NOT NULL COMMENT '加入时间',
  PRIMARY KEY (conversation_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话成员';

CREATE TABLE IF NOT EXISTS message (
  id BIGINT NOT NULL COMMENT '消息 ID',
  conversation_id BIGINT NOT NULL COMMENT '会话 ID',
  sender_id BIGINT NOT NULL COMMENT '发送人',
  msg_type VARCHAR(16) NOT NULL COMMENT 'text / image / post_card / live_card',
  content JSON NOT NULL COMMENT '文本或卡片',
  created_at DATETIME(3) NOT NULL COMMENT '发送时间',
  deleted_at DATETIME(3) NULL COMMENT '运营隐藏时间，空则对用户可见',
  PRIMARY KEY (id),
  KEY idx_conv_id (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信消息';

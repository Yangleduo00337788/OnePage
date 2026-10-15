---
title: 001_init.sql
description: onepage-message-service/db/sql/001_init.sql
order: 1
---

对应 `OnePage-Server/onepage-message-service/db/sql/001_init.sql`。库 `onepage_message`。P2 单聊。

已有环境必须建库并执行。`pair_key` 保证同一对用户只有一条会话。消息类型：`text` / `image` / `post_card`。实时走 `WS /ws/im` + Redis 频道，不改表。

## 表

| 表 | 作用 |
| --- | --- |
| conversation | 会话；单聊带 pair_key |
| conversation_member | 成员与已读游标 |
| message | 消息正文 JSON |

## SQL

原文：

```sql
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='会话';

CREATE TABLE IF NOT EXISTS conversation_member (
  conversation_id BIGINT NOT NULL COMMENT '会话 ID',
  user_id BIGINT NOT NULL COMMENT '成员',
  last_read_message_id BIGINT NULL COMMENT '已读到的消息 ID',
  joined_at DATETIME(3) NOT NULL COMMENT '加入时间',
  PRIMARY KEY (conversation_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='会话成员';

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
```

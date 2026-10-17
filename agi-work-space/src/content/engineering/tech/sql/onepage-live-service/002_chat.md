---
title: 002_chat.sql
description: onepage-live-service/db/sql/002_chat.sql
order: 2
---

对应 `OnePage-Server/onepage-live-service/db/sql/002_chat.sql`。已有 `onepage_live` 另执行。直播间文字聊天，不与私信混。

## 表

| 表 | 作用 |
| --- | --- |
| live_chat | 一场 live 场次内的文字消息 |

## SQL

原文：

```sql
USE onepage_live;

CREATE TABLE IF NOT EXISTS live_chat (
  id BIGINT NOT NULL COMMENT '消息 ID',
  session_id BIGINT NOT NULL COMMENT '场次',
  user_id BIGINT NOT NULL COMMENT '发言人 user_id',
  body VARCHAR(200) NOT NULL COMMENT '正文',
  created_at DATETIME(3) NOT NULL COMMENT '发送时间',
  PRIMARY KEY (id),
  KEY idx_session_id (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播间文字聊天';
```

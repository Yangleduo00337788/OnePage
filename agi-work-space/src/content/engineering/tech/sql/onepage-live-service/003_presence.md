---
title: 003_presence.sql
description: onepage-live-service/db/sql/003_presence.sql
order: 3
---

对应 `OnePage-Server/onepage-live-service/db/sql/003_presence.sql`。已有 `onepage_live` 且已执行 `002_chat.sql` 后另执行。给 `live_chat` 增加 `msg_type`，进房通知与发言同一张表。

## 表

| 表 | 作用 |
| --- | --- |
| live_chat | 增加 `msg_type`：`text` 发言，`join` 进房 |

## SQL

原文：

```sql
USE onepage_live;

ALTER TABLE live_chat
  ADD COLUMN msg_type VARCHAR(16) NOT NULL DEFAULT 'text' COMMENT 'text 发言 join 进房' AFTER body;
```

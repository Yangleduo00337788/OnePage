---
title: 004_notice.sql
description: onepage-live-service/db/sql/004_notice.sql
order: 4
---

对应 `OnePage-Server/onepage-live-service/db/sql/004_notice.sql`。已有 `onepage_live` 另执行。给 `live_room` 增加本场公告。列已存在会失败，只跑一次。

## 表

| 表 | 作用 |
| --- | --- |
| live_room | 增加 `notice`：本场公告，空则前端用默认欢迎文案 |

## SQL

原文：

```sql
USE onepage_live;

ALTER TABLE live_room
  ADD COLUMN notice VARCHAR(200) NOT NULL DEFAULT '' COMMENT '本场公告，空则前端用默认欢迎文案' AFTER cover_object_key;
```

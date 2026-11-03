---
title: 015_feed_explore_idx.sql
description: onepage-content-service/db/sql/015_feed_explore_idx.sql
order: 15
---

对应 `OnePage-Server/onepage-content-service/db/sql/015_feed_explore_idx.sql`。已有 `onepage_content` 必须执行。索引已存在时报 Duplicate key name，可忽略。发现流条件是公开、已发布、未删、已到展示时间，按发布时间倒序。

## SQL

原文：

```sql
ALTER TABLE post
  ADD KEY idx_feed_explore (visibility, status, deleted_at, published_at, id);
```

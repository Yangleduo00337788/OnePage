---
title: 005_peak.sql
description: onepage-live-service/db/sql/005_peak.sql
order: 5
---

对应 `OnePage-Server/onepage-live-service/db/sql/005_peak.sql`。已有 `onepage_live` 另执行。给场次记心跳在场峰值。列已存在会失败，只跑一次。

## SQL

原文：

```sql
ALTER TABLE live_session
  ADD COLUMN peak_viewer_count INT NOT NULL DEFAULT 0 COMMENT '本场心跳在场人数峰值' AFTER ended_at;
```

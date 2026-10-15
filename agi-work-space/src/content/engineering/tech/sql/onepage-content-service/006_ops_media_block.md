---
title: 006_ops_media_block.sql
description: onepage-content-service/db/sql/006_ops_media_block.sql
order: 6
---

对应 `OnePage-Server/onepage-content-service/db/sql/006_ops_media_block.sql`。已有 `onepage_content` 必须执行，否则运营隐藏媒体会因缺列失败。新装库若已跑含 `blocked_at` 的 `001_init.sql`，本脚本会报 Duplicate column，可忽略。

## SQL

原文：

```sql
-- 运营隐藏媒体。已有库执行。列已存在时跳过本脚本。
ALTER TABLE media_asset
  ADD COLUMN blocked_at DATETIME(3) NULL COMMENT '运营隐藏时间，空则对用户可见';
```

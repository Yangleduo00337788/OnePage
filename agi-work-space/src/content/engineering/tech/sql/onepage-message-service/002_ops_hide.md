---
title: 002_ops_hide.sql
description: onepage-message-service/db/sql/002_ops_hide.sql
order: 2
---

对应 `OnePage-Server/onepage-message-service/db/sql/002_ops_hide.sql`。已有 `onepage_message` 必须执行，否则运营隐藏接口会因缺列失败。新装库若已跑含 `deleted_at` 的 `001_init.sql`，本脚本会报 Duplicate column，可忽略。

## SQL

原文：

```sql
-- 运营可隐藏私信。已有库执行。列已存在时跳过本脚本。
ALTER TABLE message
  ADD COLUMN deleted_at DATETIME(3) NULL COMMENT '运营隐藏时间，空则对用户可见';
```

---
title: 013_post_ai_review_score.sql
description: onepage-content-service/db/sql/013_post_ai_review_score.sql
order: 13
---

对应 `OnePage-Server/onepage-content-service/db/sql/013_post_ai_review_score.sql`。已有 `onepage_content` 必须执行。列已存在时报 Duplicate column，可忽略。

机审给 0–100 风险分。低于运营配置的阈值自动上架；达到或超过则进人审。失败不打分。

## SQL

原文：

```sql
-- 机审风险分。已有库执行。列已存在时跳过本脚本。
ALTER TABLE post
  ADD COLUMN ai_review_score INT NULL COMMENT '机审风险分 0-100，空则未打分' AFTER ai_review_reason;
```

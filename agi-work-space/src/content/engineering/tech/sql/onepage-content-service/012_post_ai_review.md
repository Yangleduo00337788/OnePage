---
title: 012_post_ai_review.sql
description: onepage-content-service/db/sql/012_post_ai_review.sql
order: 12
---

对应 `OnePage-Server/onepage-content-service/db/sql/012_post_ai_review.sql`。已有 `onepage_content` 必须执行，否则机审写 `ai_review` 会失败。列已存在时报 Duplicate column，可忽略。Windows 用 `mysql --default-character-set=utf8mb4`，不要 PowerShell 管道。

公开笔记仍先 `pending`。文字机审通过后自动 `published`；未过或模型失败留待人审，不自动驳回。图/视频画面下一步再接 OCR。

## SQL

原文：

```sql
-- 公开笔记 AI 文字预审。已有库执行。列已存在时跳过本脚本。
ALTER TABLE post
  ADD COLUMN ai_review VARCHAR(16) NULL COMMENT '空=未跑；pass 机审过；flag 需人审；skip 未跑或失败' AFTER reject_reason,
  ADD COLUMN ai_review_reason VARCHAR(200) NULL COMMENT '机审未过或跳过原因' AFTER ai_review;
```

---
title: 014_media_ai_review.sql
description: onepage-content-service/db/sql/014_media_ai_review.sql
order: 14
---

对应 `OnePage-Server/onepage-content-service/db/sql/014_media_ai_review.sql`。已有 `onepage_content` 必须执行。列已存在时报 Duplicate column，可忽略。Windows 用 `mysql --default-character-set=utf8mb4`，不要 PowerShell 管道。

笔记图和视频在 `uploads/complete` 后异步机审，结论写在 `media_asset`。发帖只汇总文字分与媒体结论：高置信（色情/暴力或分≥80）自动驳回；广告码等灰区进人审；全低风险才上架。视频约每 5 秒一帧、最多 6 帧，看图按帧取最差；抽不出记 `skip`，下次可再试。

## SQL

原文：

```sql
ALTER TABLE media_asset
  ADD COLUMN ai_review VARCHAR(16) NULL COMMENT '空=未跑；pass 过；flag 人审；reject 高信违规；skip 未跑或失败' AFTER blocked_at,
  ADD COLUMN ai_review_reason VARCHAR(200) NULL COMMENT '机审原因' AFTER ai_review,
  ADD COLUMN ai_review_score INT NULL COMMENT '机审风险分 0-100' AFTER ai_review_reason,
  ADD COLUMN ai_review_labels VARCHAR(64) NULL COMMENT '逗号分隔 normal,ad,porn,violence' AFTER ai_review_score;

ALTER TABLE post
  ADD COLUMN ai_review_labels VARCHAR(64) NULL COMMENT '汇总标签，逗号分隔' AFTER ai_review_score;
```

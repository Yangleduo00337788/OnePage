---
title: 007_post_review.sql
description: onepage-content-service/db/sql/007_post_review.sql
order: 7
---

对应 `OnePage-Server/onepage-content-service/db/sql/007_post_review.sql`。已有 `onepage_content` 必须执行，否则审稿写 `reject_reason` 会失败。列已存在时报 Duplicate column，可忽略。

公开笔记 `status=pending`，运营通过后才 `published` 进 Feed。仅自己可见直接 `published`。已有已发布笔记保持 `published`。

## SQL

原文：

```sql
-- 创作者审稿。已有库执行。列已存在时跳过本脚本。
ALTER TABLE post
  MODIFY status VARCHAR(16) NOT NULL COMMENT 'draft / pending / published / rejected / deleted',
  ADD COLUMN reject_reason VARCHAR(200) NULL COMMENT '未通过原因，空则无' AFTER status;
```

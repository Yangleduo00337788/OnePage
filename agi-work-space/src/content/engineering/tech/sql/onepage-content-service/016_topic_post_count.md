---
title: 016_topic_post_count.sql
description: onepage-content-service/db/sql/016_topic_post_count.sql
order: 16
---

对应 `OnePage-Server/onepage-content-service/db/sql/016_topic_post_count.sql`。已有 `onepage_content` 必须执行。列已存在时报 Duplicate column，可忽略。目录接口读 `topic.post_count`，不再对 `post.body LIKE`。首次回填会扫一遍正文，只跑这一次。Windows 用 `mysql --default-character-set=utf8mb4`，不要 PowerShell 管道。

## SQL

原文：

```sql
ALTER TABLE topic
  ADD COLUMN post_count BIGINT NOT NULL DEFAULT 0 COMMENT '公开已发布正文含 #名 的大约条数' AFTER enabled;

UPDATE topic t
SET post_count = (
  SELECT COUNT(*) FROM post p
  WHERE p.deleted_at IS NULL
    AND p.status = 'published'
    AND p.visibility = 0
    AND p.body LIKE CONCAT('%#', t.name, '%')
);
```

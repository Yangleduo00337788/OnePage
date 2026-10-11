---
title: 002_p1_fix_comments.sql
description: onepage-content-service/db/sql/002_p1_fix_comments.sql
order: 3
---

对应 `OnePage-Server/onepage-content-service/db/sql/002_p1_fix_comments.sql`。只改 COMMENT，表结构不变。用 `mysql --default-character-set=utf8mb4` 执行，不要用 PowerShell 管道。

## SQL

原文：

```sql
USE onepage_content;

ALTER TABLE post COMMENT='笔记';
ALTER TABLE post
  MODIFY id BIGINT NOT NULL COMMENT '笔记 ID',
  MODIFY author_id BIGINT NOT NULL COMMENT '作者 user_id',
  MODIFY title VARCHAR(200) NULL COMMENT '标题，可空',
  MODIFY body TEXT NOT NULL COMMENT '正文',
  MODIFY post_type VARCHAR(16) NOT NULL COMMENT 'text / image / video / mixed',
  MODIFY status VARCHAR(16) NOT NULL COMMENT 'draft / published / deleted',
  MODIFY visibility TINYINT NOT NULL DEFAULT 0 COMMENT '0 公开（MVP 仅公开）',
  MODIFY published_at DATETIME(3) NULL COMMENT '首次公开时间',
  MODIFY created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  MODIFY updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  MODIFY deleted_at DATETIME(3) NULL COMMENT '软删时间';

ALTER TABLE post_media COMMENT='笔记与媒体';
ALTER TABLE post_media
  MODIFY id BIGINT NOT NULL COMMENT '关联 ID',
  MODIFY post_id BIGINT NOT NULL COMMENT '笔记 ID',
  MODIFY media_id BIGINT NOT NULL COMMENT 'media_asset.id',
  MODIFY sort_order INT NOT NULL DEFAULT 0 COMMENT '多图顺序，从小到大',
  MODIFY role VARCHAR(16) NOT NULL COMMENT 'cover / gallery / video';

ALTER TABLE outbox_event COMMENT='Outbox';
ALTER TABLE outbox_event
  MODIFY id BIGINT NOT NULL COMMENT '事件 ID',
  MODIFY aggregate_type VARCHAR(32) NOT NULL COMMENT '如 post',
  MODIFY aggregate_id VARCHAR(64) NOT NULL COMMENT '如笔记 ID',
  MODIFY event_type VARCHAR(64) NOT NULL COMMENT '如 post.published',
  MODIFY payload_json JSON NOT NULL COMMENT '事件正文',
  MODIFY status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待发送 1 已发送 2 失败',
  MODIFY created_at DATETIME(3) NOT NULL COMMENT '写入时间',
  MODIFY sent_at DATETIME(3) NULL COMMENT '发送成功时间';

ALTER TABLE post_like COMMENT='赞笔记';
ALTER TABLE post_like
  MODIFY user_id BIGINT NOT NULL COMMENT '点赞人',
  MODIFY post_id BIGINT NOT NULL COMMENT '笔记 ID',
  MODIFY created_at DATETIME(3) NOT NULL COMMENT '点赞时间';

ALTER TABLE post_favorite COMMENT='藏笔记';
ALTER TABLE post_favorite
  MODIFY user_id BIGINT NOT NULL COMMENT '收藏人',
  MODIFY post_id BIGINT NOT NULL COMMENT '笔记 ID',
  MODIFY created_at DATETIME(3) NOT NULL COMMENT '收藏时间';

ALTER TABLE comment COMMENT='评论';
ALTER TABLE comment
  MODIFY id BIGINT NOT NULL COMMENT '评论 ID',
  MODIFY post_id BIGINT NOT NULL COMMENT '笔记 ID',
  MODIFY user_id BIGINT NOT NULL COMMENT '评论人',
  MODIFY root_id BIGINT NOT NULL COMMENT '一级评论 id；一级时等于自身 id',
  MODIFY parent_id BIGINT NULL COMMENT '直接父评论；一级为空',
  MODIFY content VARCHAR(2000) NOT NULL COMMENT '正文',
  MODIFY like_count INT NOT NULL DEFAULT 0 COMMENT '获赞冗余',
  MODIFY status TINYINT NOT NULL DEFAULT 0 COMMENT '0 正常 1 删除',
  MODIFY created_at DATETIME(3) NOT NULL COMMENT '创建时间';

ALTER TABLE comment_like COMMENT='赞评论';
ALTER TABLE comment_like
  MODIFY user_id BIGINT NOT NULL COMMENT '点赞人',
  MODIFY comment_id BIGINT NOT NULL COMMENT '评论 ID',
  MODIFY created_at DATETIME(3) NOT NULL COMMENT '点赞时间';

ALTER TABLE post_view COMMENT='浏览历史（P1 暂放 content）';
ALTER TABLE post_view
  MODIFY user_id BIGINT NOT NULL COMMENT '浏览人',
  MODIFY post_id BIGINT NOT NULL COMMENT '笔记 ID',
  MODIFY viewed_at DATETIME(3) NOT NULL COMMENT '最近浏览时间';
```

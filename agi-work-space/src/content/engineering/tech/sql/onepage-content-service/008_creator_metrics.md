---
title: 008_creator_metrics.sql
description: onepage-content-service/db/sql/008_creator_metrics.sql
order: 8
---

对应 `OnePage-Server/onepage-content-service/db/sql/008_creator_metrics.sql`。已有 `onepage_content` 必须执行，否则创作者报表的分享 / 曝光 / 完播会查失败。可重复执行。

## 表

| 表 | 作用 |
| --- | --- |
| share_log | 复制链接或私信分享笔记 |
| post_cover_show | 笔记出现在发现/关注流一次记一条曝光 |
| post_play_complete | 登录用户把视频看到约 90% 记一次；同一人同一篇只一行 |

## SQL

原文：

```sql
-- 创作者报表：分享、封面曝光、视频完播。已有库执行。
CREATE TABLE IF NOT EXISTS share_log (
  id BIGINT NOT NULL COMMENT '分享记录 ID',
  user_id BIGINT NOT NULL COMMENT '分享人',
  post_id BIGINT NOT NULL COMMENT '笔记',
  channel VARCHAR(16) NOT NULL COMMENT 'link / im',
  created_at DATETIME(3) NOT NULL COMMENT '分享时间',
  PRIMARY KEY (id),
  KEY idx_post_created (post_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记分享';

CREATE TABLE IF NOT EXISTS post_cover_show (
  id BIGINT NOT NULL COMMENT '曝光 ID',
  viewer_id BIGINT NULL COMMENT '浏览人，未登录空',
  post_id BIGINT NOT NULL COMMENT '笔记',
  created_at DATETIME(3) NOT NULL COMMENT '出现在 Feed 的时间',
  PRIMARY KEY (id),
  KEY idx_post_created (post_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='封面在 Feed 出现一次计一次曝光';

CREATE TABLE IF NOT EXISTS post_play_complete (
  user_id BIGINT NOT NULL COMMENT '观看人',
  post_id BIGINT NOT NULL COMMENT '视频笔记',
  created_at DATETIME(3) NOT NULL COMMENT '首次完播时间',
  PRIMARY KEY (user_id, post_id),
  KEY idx_post_created (post_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频播放到 90% 记一次完播';
```

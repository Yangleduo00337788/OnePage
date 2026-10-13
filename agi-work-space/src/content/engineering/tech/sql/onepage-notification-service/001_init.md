---
title: 001_init.sql
description: onepage-notification-service/db/sql/001_init.sql
order: 1
---

对应 `OnePage-Server/onepage-notification-service/db/sql/001_init.sql`。库 `onepage_notification`。P2 通知收件箱。

已有环境必须建库并执行本脚本，否则通知接口失败。可重复执行。同一收件人同一 `aggregate_key` 只保留一行，再投递会刷新未读。

## 表

| 表 | 作用 |
| --- | --- |
| notification | 收件箱；按收件人时间倒序 |

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| user_id | BIGINT | 收件人 |
| type | VARCHAR(32) | `comment` / `reply` / `mention` / `like_post` / `favorite_post` / `like_comment` / `follow` |
| actor_id | BIGINT 可空 | 触发者 |
| aggregate_key | VARCHAR(128) | 去重键 |
| post_id | BIGINT 可空 | 相关笔记 |
| comment_id | BIGINT 可空 | 相关评论 |
| payload | JSON 可空 | 扩展 |
| read_at | DATETIME(3) 可空 | 空为未读 |
| created_at | DATETIME(3) | 创建或最近合并时间 |

## SQL

原文：

```sql
CREATE DATABASE IF NOT EXISTS onepage_notification DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE onepage_notification;

CREATE TABLE IF NOT EXISTS notification (
  id BIGINT NOT NULL COMMENT '通知 ID',
  user_id BIGINT NOT NULL COMMENT '收件人',
  type VARCHAR(32) NOT NULL COMMENT 'comment/reply/mention/like_post/favorite_post/like_comment/follow',
  actor_id BIGINT NULL COMMENT '触发者',
  aggregate_key VARCHAR(128) NOT NULL COMMENT '去重键，如同帖同人点赞',
  post_id BIGINT NULL COMMENT '相关笔记',
  comment_id BIGINT NULL COMMENT '相关评论',
  payload JSON NULL COMMENT '扩展',
  read_at DATETIME(3) NULL COMMENT '已读时间；空为未读',
  created_at DATETIME(3) NOT NULL COMMENT '创建或最近一次合并时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_aggregate (user_id, aggregate_key),
  KEY idx_user_created (user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='通知收件箱';
```

---
title: 002_follow.sql
description: onepage-user-service/db/sql/002_follow.sql
order: 2
---

对应 `OnePage-Server/onepage-user-service/db/sql/002_follow.sql`。库 `onepage_user`。P2 关注边。

已有库必须执行本脚本，否则关注接口会报没有 `user_follow`。可重复执行。

## 表

| 表 | 作用 |
| --- | --- |
| user_follow | 单向关注；同一对用户仅一行 |

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| follower_id | BIGINT | 关注人；PK 之一 |
| followee_id | BIGINT | 被关注人；PK 之一 |
| created_at | DATETIME(3) | 关注时间；列表按此倒序 |

## SQL

原文：

```sql
CREATE TABLE IF NOT EXISTS user_follow (
  follower_id BIGINT NOT NULL COMMENT '关注人 user_id',
  followee_id BIGINT NOT NULL COMMENT '被关注人 user_id',
  created_at DATETIME(3) NOT NULL COMMENT '关注时间',
  PRIMARY KEY (follower_id, followee_id),
  KEY idx_followee_created (followee_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单向关注';
```

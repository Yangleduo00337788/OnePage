---
title: 001_init.sql
description: onepage-user-service/db/sql/001_init.sql
order: 1
---

对应 `OnePage-Server/onepage-user-service/db/sql/001_init.sql`。库 `onepage_user`。P0 仅资料 + 计数。

## 表

| 表 | 作用 |
| --- | --- |
| user_profile | 资料、一页号 |
| user_stat | 关注/粉丝/获赞藏计数 |

`user_follow` 见 [002_follow.sql](/engineering/tech/sql/onepage-user-service/002_follow)。`browse_history` 本脚本没有，浏览列表读 content `post_view`。

## 字段

### user_profile

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| user_id | BIGINT PK | 与 auth_account.id 一致 |
| page_id | VARCHAR(16) UK | 一页号，创建后不可改 |
| nickname | VARCHAR(64) | |
| avatar_object_key | VARCHAR(512) 可空 | 头像 MinIO key |
| cover_object_key | VARCHAR(512) 可空 | 主页封面 |
| bio | VARCHAR(500) 可空 | |
| gender | TINYINT | 0 未知 1 男 2 女 |
| created_at / updated_at | DATETIME(3) | |

### user_stat

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| user_id | BIGINT PK | |
| following_count | INT | 关注数 |
| follower_count | INT | 粉丝数 |
| like_received_count | INT | 笔记获赞 |
| favorite_received_count | INT | 笔记被藏 |
| updated_at | DATETIME(3) | |

## SQL

原文：

```sql
CREATE TABLE IF NOT EXISTS user_profile (
  user_id BIGINT PRIMARY KEY COMMENT '与 auth_account.id 一致',
  page_id VARCHAR(16) NOT NULL COMMENT '一页号，对外展示，创建后不可改',
  nickname VARCHAR(64) NOT NULL COMMENT '昵称',
  avatar_object_key VARCHAR(512) NULL COMMENT '头像 MinIO object key',
  cover_object_key VARCHAR(512) NULL COMMENT '主页封面 MinIO object key',
  bio VARCHAR(500) NULL COMMENT '签名',
  gender TINYINT NOT NULL DEFAULT 0 COMMENT '0 未知 1 男 2 女',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_page_id (page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资料';

CREATE TABLE IF NOT EXISTS user_stat (
  user_id BIGINT PRIMARY KEY COMMENT '与 user_profile.user_id 一致',
  following_count INT NOT NULL DEFAULT 0 COMMENT '关注数',
  follower_count INT NOT NULL DEFAULT 0 COMMENT '粉丝数',
  like_received_count INT NOT NULL DEFAULT 0 COMMENT '笔记获赞数',
  favorite_received_count INT NOT NULL DEFAULT 0 COMMENT '笔记被藏数',
  updated_at DATETIME(3) NOT NULL COMMENT '最后累加时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户计数';
```

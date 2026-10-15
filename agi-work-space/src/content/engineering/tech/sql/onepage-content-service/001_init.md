---
title: 001_init.sql
description: onepage-content-service/db/sql/001_init.sql
order: 1
---

对应 `OnePage-Server/onepage-content-service/db/sql/001_init.sql`。库 `onepage_content`。P0 仅媒体。笔记见 [002_p1.sql](/engineering/tech/sql/onepage-content-service/002_p1)。

## 表

| 表 | 作用 |
| --- | --- |
| media_asset | 预签名上传对象 |

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 对外 mediaId |
| owner_id | BIGINT | 上传者 user_id |
| bucket | VARCHAR(64) | MinIO 桶名，默认 onepage-media |
| object_key | VARCHAR(512) | 对象键 |
| mime_type | VARCHAR(128) | 如 image/jpeg |
| size_bytes | BIGINT | |
| duration_sec | INT 可空 | 视频秒；图片空 |
| status | TINYINT | 0 上传中 1 就绪 2 失败 |
| created_at | DATETIME(3) | |
| blocked_at | DATETIME(3) 可空 | 运营隐藏 |

## SQL

原文：

```sql
CREATE TABLE IF NOT EXISTS media_asset (
  id BIGINT PRIMARY KEY COMMENT '对外 mediaId',
  owner_id BIGINT NOT NULL COMMENT '上传者 user_id',
  bucket VARCHAR(64) NOT NULL COMMENT 'MinIO 桶名，默认 onepage-media',
  object_key VARCHAR(512) NOT NULL COMMENT '对象键',
  mime_type VARCHAR(128) NOT NULL COMMENT '如 image/jpeg',
  size_bytes BIGINT NOT NULL COMMENT '字节数',
  duration_sec INT NULL COMMENT '视频时长秒；图片空',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 上传中 1 就绪 2 失败',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  blocked_at DATETIME(3) NULL COMMENT '运营隐藏时间，空则对用户可见',
  KEY idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='媒体对象';
```

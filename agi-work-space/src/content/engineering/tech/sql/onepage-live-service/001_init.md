---
title: 001_init.sql
description: onepage-live-service/db/sql/001_init.sql
order: 1
---

对应 `OnePage-Server/onepage-live-service/db/sql/001_init.sql`。库 `onepage_live`。P4 开播/列表。

已有环境必须建库并执行本脚本。可重复执行。推流地址不落库，由 `LIVE_PUSH_BASE` / `LIVE_HLS_BASE` + `stream_key` 拼出。

## 表

| 表 | 作用 |
| --- | --- |
| live_room | 主播房间 |
| live_session | 一场开播；`status=live` 才进列表 |

## SQL

原文：

```sql
CREATE DATABASE IF NOT EXISTS onepage_live DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE onepage_live;

CREATE TABLE IF NOT EXISTS live_room (
  id BIGINT NOT NULL COMMENT '房间 ID',
  owner_id BIGINT NOT NULL COMMENT '主播 user_id',
  title VARCHAR(100) NOT NULL COMMENT '标题',
  category VARCHAR(32) NOT NULL DEFAULT '' COMMENT '分类，空为未分',
  cover_object_key VARCHAR(512) NULL COMMENT '封面 object key 或公开 URL',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播房间';

CREATE TABLE IF NOT EXISTS live_session (
  id BIGINT NOT NULL COMMENT '场次 ID',
  room_id BIGINT NOT NULL COMMENT '房间',
  stream_key VARCHAR(64) NOT NULL COMMENT '推流 key',
  status VARCHAR(16) NOT NULL COMMENT 'idle/live/ended',
  started_at DATETIME(3) NULL COMMENT '开播时间',
  ended_at DATETIME(3) NULL COMMENT '关播时间',
  PRIMARY KEY (id),
  KEY idx_room_status (room_id, status),
  KEY idx_status_started (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播场次';
```

---
title: 006_official_badge.sql
description: onepage-user-service/db/sql/006_official_badge.sql
order: 6
---

对应 `OnePage-Server/onepage-user-service/db/sql/006_official_badge.sql`。已有 `onepage_user` 必须执行。列已存在时跳过 `ADD COLUMN`。

`user_profile.official = 1` 出黄 V，文案「一页官方认证号」。脚本同时把昵称「一页官方」置为官方号。Windows 执行用 `mysql --default-character-set=utf8mb4`，不要 PowerShell 管道。

## 列

| 表 | 列 | 作用 |
| --- | --- | --- |
| user_profile | official | 1 一页官方认证号 |

## SQL

原文：

```sql
-- 一页官方认证号。已有库必须执行。列已存在时跳过 ADD。Windows 须 mysql --default-character-set=utf8mb4。
ALTER TABLE user_profile
  ADD COLUMN official TINYINT NOT NULL DEFAULT 0 COMMENT '1 一页官方认证号' AFTER verified;

UPDATE user_profile SET official = 1 WHERE nickname = '一页官方';
```

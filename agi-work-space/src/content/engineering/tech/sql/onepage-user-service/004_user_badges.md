---
title: 004_user_badges.sql
description: onepage-user-service/db/sql/004_user_badges.sql
order: 4
---

对应 `OnePage-Server/onepage-user-service/db/sql/004_user_badges.sql`。已有 `onepage_user` 必须执行。列已存在时跳过本脚本（`ADD COLUMN` 会报 Duplicate column）。

运营通过主播入驻 / 大 V 申请后，`user_profile.live_host` / `verified` 置 1，社区资料与 `UserSummary` 出标识。只置位、不自动取消。

## 列

| 表 | 列 | 作用 |
| --- | --- | --- |
| user_profile | live_host | 1 主播入驻通过 |
| user_profile | verified | 1 大 V 认证通过 |

## SQL

原文：

```sql
-- 主播入驻 / 大 V 标识。已有库必须执行。列已存在时跳过本脚本。
ALTER TABLE user_profile
  ADD COLUMN live_host TINYINT NOT NULL DEFAULT 0 COMMENT '1 主播入驻通过' AFTER gender;
ALTER TABLE user_profile
  ADD COLUMN verified TINYINT NOT NULL DEFAULT 0 COMMENT '1 大 V 认证通过' AFTER live_host;
```

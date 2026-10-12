---
title: 004_post_settings.sql
description: onepage-content-service/db/sql/004_post_settings.sql
order: 5
---

对应 `OnePage-Server/onepage-content-service/db/sql/004_post_settings.sql`。给已有 `post` 加原创 / 内容类型 / 关联活动快照，并标明 `visibility` 0 公开、1 仅自己。列已存在时跳过，可对已有库重复执行。新库若 `002_p1.sql` 已含这些列，跑本脚本只会改 `visibility` 注释。

在 `onepage_content` 上执行（服务已启动时不用重启，下一请求即可）。

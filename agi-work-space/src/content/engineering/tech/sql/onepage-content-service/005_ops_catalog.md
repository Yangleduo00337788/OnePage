---
title: 005_ops_catalog.sql
description: onepage-content-service/db/sql/005_ops_catalog.sql
order: 6
---

对应 `OnePage-Server/onepage-content-service/db/sql/005_ops_catalog.sql`。运营分类 / 活动 / 话题表，并加宽 `post.category`。已有 `onepage_content` 必须执行，否则运营 CRUD 和社区 Tab 仍走代码常量兜底。可重复执行。

## SQL

见同名 `*.sql`。种子写入现有八个分类、e1/e2 活动、三条官方话题。

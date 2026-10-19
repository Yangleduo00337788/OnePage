---
title: 010_creator_faq.sql
description: onepage-content-service/db/sql/010_creator_faq.sql
order: 10
---

对应 `OnePage-Server/onepage-content-service/db/sql/010_creator_faq.sql`。已有 `onepage_content` 必须执行，否则创作者字典为空。可重复执行。种子问答与创作者中心四个 Tab 对齐。

## 表

| 表 | 作用 |
| --- | --- |
| creator_faq | 创作字典；`group_id` 为 account / create / data / faq |

## SQL

原文见 Server 同名文件。种子 12 条，`INSERT IGNORE`。

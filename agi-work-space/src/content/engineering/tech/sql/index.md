---
title: 数据库脚本
description: 与 OnePage-Server 中 SQL 文件同名同路径对照
order: 5
---

本目录只收 **项目里已经存在的脚本**。文档文件名 = Server 里的 `.sql` 文件名，不要自编 `onepage_auth.sql` 这类名字。可执行文件只在 `OnePage-Server`，这里是说明 + 原文。

后续 Agent **新加表**：先在对应服务目录按已有规则起名（`004_xxx.sql`），本目录同步建同名 `.md`，再写入 `OnePage-Server/<服务>/db/sql/`。还没进仓库的脚本不要先造文件名。未建表的字段仍看 [数据库设计](/engineering/tech/database-schema)。

| 工作空间文档 | 项目中的文件 |
| --- | --- |
| [onepage-auth-service / 001_init](/engineering/tech/sql/onepage-auth-service/001_init) | `onepage-auth-service/db/sql/001_init.sql` |
| [onepage-user-service / 001_init](/engineering/tech/sql/onepage-user-service/001_init) | `onepage-user-service/db/sql/001_init.sql` |
| [002_follow](/engineering/tech/sql/onepage-user-service/002_follow) | `onepage-user-service/db/sql/002_follow.sql` |
| [onepage-content-service / 001_init](/engineering/tech/sql/onepage-content-service/001_init) | `onepage-content-service/db/sql/001_init.sql` |
| [002_p1](/engineering/tech/sql/onepage-content-service/002_p1) | `onepage-content-service/db/sql/002_p1.sql` |
| [002_p1_fix_comments](/engineering/tech/sql/onepage-content-service/002_p1_fix_comments) | `onepage-content-service/db/sql/002_p1_fix_comments.sql` |
| [003_category](/engineering/tech/sql/onepage-content-service/003_category) | `onepage-content-service/db/sql/003_category.sql` |
| [004_post_settings](/engineering/tech/sql/onepage-content-service/004_post_settings) | `onepage-content-service/db/sql/004_post_settings.sql` |
| [005_ops_catalog](/engineering/tech/sql/onepage-content-service/005_ops_catalog) | `onepage-content-service/db/sql/005_ops_catalog.sql` |
| [006_ops_media_block](/engineering/tech/sql/onepage-content-service/006_ops_media_block) | `onepage-content-service/db/sql/006_ops_media_block.sql` |
| [007_post_review](/engineering/tech/sql/onepage-content-service/007_post_review) | `onepage-content-service/db/sql/007_post_review.sql` |
| [onepage-notification-service / 001_init](/engineering/tech/sql/onepage-notification-service/001_init) | `onepage-notification-service/db/sql/001_init.sql` |
| [onepage-message-service / 001_init](/engineering/tech/sql/onepage-message-service/001_init) | `onepage-message-service/db/sql/001_init.sql` |
| [002_ops_hide](/engineering/tech/sql/onepage-message-service/002_ops_hide) | `onepage-message-service/db/sql/002_ops_hide.sql` |
| [onepage-moderation-service / 001_init](/engineering/tech/sql/onepage-moderation-service/001_init) | `onepage-moderation-service/db/sql/001_init.sql` |
| [onepage-live-service / 001_init](/engineering/tech/sql/onepage-live-service/001_init) | `onepage-live-service/db/sql/001_init.sql` |
| [002_chat](/engineering/tech/sql/onepage-live-service/002_chat) | `onepage-live-service/db/sql/002_chat.sql` |
| [00-databases](/engineering/tech/sql/docker/00-databases) | `docker/mysql/init/00-databases.sql` |
| [01-tables](/engineering/tech/sql/docker/01-tables) | `docker/mysql/init/01-tables.sql` |

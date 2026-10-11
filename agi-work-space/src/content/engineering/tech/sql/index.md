---
title: 数据库脚本
description: 全库可执行 SQL（带注释），与字段字典对照
order: 5
---

全库 **可执行 SQL** 放在本目录；字段说明对照 [数据库设计](/engineering/tech/database-schema)。改表：**先改本目录脚本 + 字段字典**，再同步到 `OnePage-Server/*/db/sql/`。

本地 IDEA（不用 Docker）：在本机 MySQL 先跑建库，再按服务 `USE` 后执行对应文件。P0 只需 auth / user / content 三库里已标注「已落地」的表。

## 文件

| 文件 | 库 | 阶段 |
| --- | --- | --- |
工作空间文档页只渲染 Markdown，`.sql` 请在仓库里打开：`agi-work-space/src/content/engineering/tech/sql/`。

| 文件 | 库 | 阶段 |
| --- | --- | --- |
| `00-create-databases.sql` | 全部建库 | 一次性 |
| `onepage_auth.sql` | onepage_auth | **P0 已落地** |
| `onepage_user.sql` | onepage_user | P0 资料；关注/足迹 P2 |
| `onepage_content.sql` | onepage_content | P0 媒体；P1 笔记 + 赞藏评合库 |
| `onepage_social.sql` | onepage_social | 目标库脚本；P1 未单独起服务 |
| `onepage_message.sql` | onepage_message | P2 |
| `onepage_notification.sql` | onepage_notification | P2 |
| `onepage_live.sql` | onepage_live | P4 |
| `onepage_moderation.sql` | onepage_moderation | P5 |
| `outbox_event.sql` | 模板 | 随发帖/互动启用 |

代码仓执行入口：`OnePage-Server/onepage-*-service/db/sql/001_init.sql`（P0），content 另有 `002_p1.sql`（P1）、`003_category.sql`（分类列），须与本目录对应表一致。

## 约定

- 主键 `BIGINT`，应用生成雪花，**不用** `AUTO_INCREMENT`。
- 时间 `DATETIME(3)`。
- **禁止跨库外键**，只存对方 ID。
- 不用 Flyway；新变更新增 `00x_*.sql` 并在服务 `db/CHANGELOG.md` 记一行。

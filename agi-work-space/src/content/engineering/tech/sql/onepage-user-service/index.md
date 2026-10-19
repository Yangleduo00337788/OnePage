---
title: onepage-user-service
description: 对应 OnePage-Server/onepage-user-service/db/sql/
order: 2
---

用户服务 DDL。

- [001_init.sql](/engineering/tech/sql/onepage-user-service/001_init) — 资料与计数
- [002_follow.sql](/engineering/tech/sql/onepage-user-service/002_follow) — 关注边（已有库必须执行）
- [003_unfollow_log.sql](/engineering/tech/sql/onepage-user-service/003_unfollow_log) — 取关日志（已有库必须执行）
- [004_user_badges.sql](/engineering/tech/sql/onepage-user-service/004_user_badges) — 主播/大 V 标识列（已有库必须执行）
- [005_unfollow_log_comments.sql](/engineering/tech/sql/onepage-user-service/005_unfollow_log_comments) — 修正取关日志中文 COMMENT
- [006_official_badge.sql](/engineering/tech/sql/onepage-user-service/006_official_badge) — 一页官方认证号（已有库必须执行）

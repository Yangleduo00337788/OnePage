---
title: onepage-message-service
description: 对应 OnePage-Server/onepage-message-service/db/sql/
order: 5
---

私信服务 DDL。

- [001_init.sql](/engineering/tech/sql/onepage-message-service/001_init) — 会话与消息（已有环境必须建库并执行）
- [002_ops_hide.sql](/engineering/tech/sql/onepage-message-service/002_ops_hide) — 已有库给 `message` 加 `deleted_at`

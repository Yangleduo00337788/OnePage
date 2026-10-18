---
title: onepage-live-service
description: 对应 OnePage-Server/onepage-live-service/db/sql/
order: 7
---

直播服务 DDL。

- [001_init.sql](/engineering/tech/sql/onepage-live-service/001_init) — 房间与场次（已有环境必须建库并执行）
- [002_chat.sql](/engineering/tech/sql/onepage-live-service/002_chat) — 直播间文字聊天（已有 `onepage_live` 另执行）
- [003_presence.sql](/engineering/tech/sql/onepage-live-service/003_presence) — `live_chat.msg_type`（已有库另执行）
- [004_notice.sql](/engineering/tech/sql/onepage-live-service/004_notice) — `live_room.notice` 本场公告（已有库另执行）

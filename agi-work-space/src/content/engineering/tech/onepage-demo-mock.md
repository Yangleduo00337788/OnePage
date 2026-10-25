---
title: OnePage-demo 内存 Mock 覆盖
description: 静态站演示数据与未接真后端的边界
order: 90
---

# OnePage-demo 内存 Mock 覆盖

静态站通过 `installDemoBridge` 把 `api.*` 全部接到 `OnePage-demo/src/handle.ts`（`localStorage` 键 `onepage.web.db`）。界面在演示模式下显示灰色文案 **当前模式：演示模式**（`isDemoMode()`）。

## 已 Mock（可完整走通 UI）

| 域 | 说明 |
| --- | --- |
| 鉴权 | 短信（任意 4～8 位码）、密码登录/注册、重置密码、账号资料；OAuth 走演示 `__onepageOauth`（未登录则登种子首用户）。种子例：`林舟` / `linzhou` / `13800138000` + `Linzhou8`；运营 `苏晚` + `Suwan8x`（`staff`）。社区点「发布」应**新标签**开 `/creator`，禁止在当前标签 `assign` 以免顶掉社区壳 |
| 用户 | 资料、关注、主页笔记/赞藏/历史、获赞与收藏列表 |
| Feed / 搜索 | `explore` / `following`、搜索笔记/用户/话题/直播（内存过滤，非 ES） |
| 笔记 | 发/改/删、可见性、浏览/完播、赞藏评、分享链接与 IM、评论两层与 @ |
| 创作者 | 我的笔记、统计报表、发布链路、预签名上传（`blobs` 内存 URL） |
| 直播 | 列表/房间/开播关播/公告/聊天/心跳/分享；播放为种子 MP4，非真 OBS 推流 |
| 通知 / 私信 | 通知分组与已读；会话与消息；`WS /ws/im` 由 bootstrap 假 Socket |
| 耶耶 AI | `bootstrap`、非流式 `chat`、流式 `chat/stream`（模板回复）、发帖润色 |
| 运营 | `handleOps` 覆盖工单、用户、笔记审阅、评论、媒体库、分类/活动/话题/公告/字典、帮助申请、私信巡查、AI 配置探测（模拟）、社区规则 |
| 目录 | `GET /catalog`、`/catalog/faq`、`/catalog/topics` |

## 演示与真机差异（预期）

- 数据仅存浏览器，清库或 `seedVer` 变更会重种。
- 搜索/推荐无真实 ES 与个性化排序。
- 上传不落 MinIO；外链视频/图依赖种子或 `public/media`。
- 直播无 SRS/RTMP；礼物、连麦等产品非目标能力未做。
- 接网关的 5174–5176 产品前端**不**注入 bridge，不显示「演示模式」。

## 维护

新增 `api-client` 方法时，须在 `handle.ts`（及 `handleOps`）补路由，否则会 `404 未实现`。

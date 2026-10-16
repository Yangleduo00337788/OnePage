---
title: 领域设计
description: ONEPAGE 限界上下文与核心对象
order: 5
---

RD 维护。服务边界以 [架构与 MVP 路线图](/engineering/tech/mvp-roadmap) 为准。

## 限界上下文

| 上下文 | 服务 | 核心对象 |
| --- | --- | --- |
| 身份 | onepage-auth-service | 凭证、OAuth 绑定、Session |
| 用户 | onepage-user-service | 用户、资料、一页号、关注边 |
| 内容 | onepage-content-service | 笔记、媒体、草稿、浏览历史 |
| 社交 | onepage-social-service | 赞、藏、评论、分享 |
| 私信 | onepage-message-service | 会话、消息；运营隐藏用 `deleted_at` |
| 通知 | onepage-notification-service | 通知收件箱 |
| 检索 | onepage-search-service | 索引文档、查询 |
| 智能 | onepage-ai-service | 生成任务、审计 |
| 治理 | onepage-moderation-service | 举报单、处理记录、员工校验（P5；前端 `app-ops`。不另建 admin 服务） |
| 直播 | onepage-live-service | 房间、场次 |
| 工作空间（本仓） | agi-work-space | 文档 Doc、角色 Role、工作目录 |

## 核心对象（产品）

| 对象 | 关键属性 | 主权服务 |
| --- | --- | --- |
| User | `userId`、一页号 `pageId` | user |
| Post | `postId`、作者、类型图文视频、`status`（含 draft）、`visibility`（0 公开 / 1 自己 / 2 好友） | content |
| Comment | `commentId`、`rootId`、`parentId` | social |
| Conversation | 成员、最后消息时间 | message |
| Notification | 类型、聚合键、已读 | notification |
| LiveRoom | 推流/播放地址、状态 | live |

跨服务只传 ID，禁止跨库 JOIN。

## 不变量（示例）

- 一页号创建后不可变。
- 点赞/收藏对同一用户与目标唯一。
- 关注单向、同一对用户仅一行；不能关注自己。
- 本人收藏/点赞/浏览列表只出仍可见的已发布帖（他人仅自己可见不出现）。
- 私信与通知数据不混表。通知按 `(user_id, aggregate_key)` 合并；不能给自己发通知。
- 帖子删除后社交动作不可新增（可读历史策略 PM 定）。

## 工作空间对象

见原模版：Doc、Role、工作目录、代码空间 — 仅存在于 `agi-work-space`，见 [工作空间写回约定](/engineering/process/workspace-writeback)。

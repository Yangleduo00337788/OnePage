---
title: API 接口明细
description: 接口路径与 JSON 字段（查字典用）
order: 5
---

REST 路径与请求/响应字段一览。共性规则见 [接口契约](/engineering/process/contracts)；落库见 [数据库设计](/engineering/tech/database-schema)、[数据库脚本](/engineering/tech/sql)。**改接口先改本页。**

P0 已实现：Auth（短信 mock 码 `888888`）、设密/找回密码、`GET/PATCH /users/me`、Uploads presign/complete。OAuth 未配 client 时返回配置缺失。关注/列表等仍是契约，代码未做。登录注册图见 [登录注册流程](/engineering/tech/auth-flows)。

## 按模块查（用编辑器搜索标题）

| 模块 | 阶段 | 文内标题 |
| --- | --- | --- |
| 登录注册 | P0 | `## Auth` |
| 用户与关注 | P0–P2 | `## Users` |
| 上传 | P0 | `## Uploads` |
| 笔记 | P1 | `## Posts` |
| 赞藏 / 评论 | P1 | `## Reactions` · `## Comments` |
| 通知 / 私信 | P2 | `## Notifications` · `## Conversations` |
| 搜索 / AI | P3 | `## Search` · `## AI` |
| 直播 / 举报 | P4–P5 | `## Live` · `## Reports` |

下面几个 JSON 形状会反复出现。

## 公共类型

### UserBrief（列表/卡片常用）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| userId | string | 内部 ID |
| pageId | string | 一页号 |
| nickname | string | |
| avatarUrl | string | 可空，CDN 或网关签名 URL |

### PostBrief

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| postId | string | |
| author | UserBrief | |
| title | string | 可空 |
| coverUrl | string | 可空 |
| postType | string | text / image / video / mixed |
| likeCount | number | |
| favoriteCount | number | |
| commentCount | number | |
| publishedAt | string | ISO-8601 |

### CursorPage

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| items | array | |
| nextCursor | string | 无更多时 null |
| hasMore | boolean | |

---

## Auth（P0）

### POST `/api/v1/auth/sms/send`

发送登录/注册验证码。

**Body**

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| phone | string | 是 |
| scene | string | 是：`login` / `register` / `reset` |

**Response 200** `{ "cooldownSec": 60 }`

### POST `/api/v1/auth/sms/login`

**Body**：`phone`, `code`  
**Response 200**：`{ "userId", "pageId", "nickname", "avatarUrl" }`（并 Set-Cookie）

### POST `/api/v1/auth/register`

账密注册。**Body**：`phone?`, `email?`, `password`, `nickname?`  
`password`：8～32 位，须同时包含字母和数字。  
**Response 201**：同登录用户摘要 + Cookie

### POST `/api/v1/auth/login`

账密登录。**Body**：`account`（手机或邮箱）, `password`  
**Response 200**：用户摘要 + Cookie

### POST `/api/v1/auth/logout`

**Response 200** `{ "ok": true }`。由网关清 Redis Session（不要再转发 auth invalidate，否则请求会挂住）。

### GET `/api/v1/auth/account`

已登录。**Response 200** `{ "phone", "email", "hasPassword" }`（空字符串表示未绑）

### POST `/api/v1/auth/password`

已登录设密 / 改密。**Body**：`password`（必填，规则同注册）；已有密码时 `oldPassword` 必填。  
**Response 200**：同 `GET /account`

### POST `/api/v1/auth/password/reset`

未登录找回。**Body**：`phone`, `code`（`scene=reset` 的验证码）, `password`  
该手机须已有账号。成功后种 Cookie 并登录。

### GET `/api/v1/auth/oauth/{provider}/authorize`

`provider`: `github` | `google` → 302 到 IdP

### GET `/api/v1/auth/oauth/{provider}/callback`

OAuth 回调 → 建号/绑定 → Cookie → 302 到前端首页

---

## Users（P0–P2）

### GET `/api/v1/users/me`

当前登录用户。**Response 200**

| 字段 | 类型 |
| --- | --- |
| userId | string |
| pageId | string |
| nickname | string |
| avatarUrl | string |
| coverUrl | string |
| bio | string |
| gender | number |
| stats | object 见下 |

`stats`: `followingCount`, `followerCount`, `likeReceivedCount`, `favoriteReceivedCount`

### PATCH `/api/v1/users/me`

**Body**（均可选）：`nickname`, `bio`, `gender`, `avatarMediaId`, `coverMediaId`  
**Response 200**：完整 profile

### GET `/api/v1/users/{pageId}`

他人主页（公开字段 + stats）

### GET `/api/v1/users/{pageId}/posts`

Query：`cursor`, `limit` → `CursorPage<PostBrief>`

### GET `/api/v1/users/me/favorites` | `/likes` | `/history`

Query：`cursor`, `limit` → `CursorPage<PostBrief>`（history 仅本人）

### GET `/api/v1/users/{pageId}/following` | `/followers`

Query：`cursor`, `limit` → `CursorPage<UserBrief>`

### GET `/api/v1/users/me/likes-received` | `/favorites-received`

获赞/被收藏列表（点击统计数字进入）→ `CursorPage<PostBrief>` 或聚合项

### POST `/api/v1/users/{pageId}/follow`

**Response 201** / 已关注 **200**

### DELETE `/api/v1/users/{pageId}/follow`

取消关注 **204**

---

## Uploads（P0）

### POST `/api/v1/uploads/presign`

**Body**：`mimeType`, `sizeBytes`, `purpose`（`avatar` | `cover` | `post_image` | `post_video`）  
**Response 200**：`{ "mediaId", "uploadUrl", "objectKey", "expiresAt" }`

### POST `/api/v1/uploads/complete`

**Body**：`mediaId`  
**Response 200**：`{ "mediaId", "status": "ready" }`

---

## Posts（P1）

### POST `/api/v1/posts`

创建并发布（或 draft 另议）。**Body**

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| title | string | 否 |
| body | string | 是 |
| mediaIds | string[] | 否 |
| postType | string | 是 |

**Response 201**：`{ "postId", "publishedAt" }`

### GET `/api/v1/posts/{postId}`

**Response 200**：详情含 `body`, `media[]`（url, mime, width/height 可选）, `author`, counts, `likedByMe`, `favoritedByMe`

### GET `/api/v1/feeds/following` | `/feeds/explore`

Query：`cursor`, `limit` → `CursorPage<PostBrief>`

### DELETE `/api/v1/posts/{postId}`

作者软删 **204**

### POST `/api/v1/posts/{postId}/view`

记录浏览历史 **204**（登录用户）

---

## AI（P1 美化 / P3 问答）

### POST `/api/v1/ai/posts/polish`

**Body**：`{ "title?", "body" }`  
**Response 200**：`{ "title?", "body" }`

### POST `/api/v1/ai/chat`

**Body**：`{ "question", "sessionId?" }`  
**Response 200**：`{ "answer", "citations": [{ "postId", "snippet" }] }`

---

## Reactions（P1）

### POST `/api/v1/reactions/posts/{postId}/like`

Toggle 或纯 POST 点赞；**Response 200** `{ "liked": true, "likeCount" }`  
Header 可选 `Idempotency-Key`

### DELETE 同上 → 取消赞

### POST `/api/v1/reactions/posts/{postId}/favorite`

**Response 200** `{ "favorited", "favoriteCount" }`

### POST `/api/v1/reactions/comments/{commentId}/like`

**Response 200** `{ "liked", "likeCount" }`

---

## Comments（P1）

### GET `/api/v1/posts/{postId}/comments`

Query：`rootId?`（拉某楼回复）, `offset`, `limit`  
**Response 200**：`{ "items": Comment[], "total" }`

**Comment**

| 字段 | 类型 |
| --- | --- |
| commentId | string |
| author | UserBrief |
| content | string |
| parentId | string 可空 |
| rootId | string |
| likeCount | number |
| likedByMe | boolean |
| replyCount | number |
| createdAt | string |

### POST `/api/v1/posts/{postId}/comments`

**Body**：`content`, `parentId?`, `mentionPageIds?`（@）  
**Response 201**：Comment

### DELETE `/api/v1/comments/{commentId}`

**Response 204**

---

## Share（P1–P2）

### POST `/api/v1/posts/{postId}/share/link`

**Response 200**：`{ "shareUrl" }`

### POST `/api/v1/posts/{postId}/share/im`

**Body**：`{ "conversationId" }` 或 `{ "targetUserId" }`  
**Response 201**：消息 id

---

## Notifications（P2）

### GET `/api/v1/notifications`

Query：`cursor`, `limit`, `unreadOnly?`  
**Response**：`CursorPage<NotificationItem>`

**NotificationItem**：`id`, `type`, `actor` UserBrief, `postId?`, `commentId?`, `read`, `createdAt`

### POST `/api/v1/notifications/read`

**Body**：`{ "ids": [] }` 或 `{ "all": true }`  
**Response 204**

### GET `/api/v1/notifications/unread-count`

**Response**：`{ "count" }`

---

## Conversations / IM（P2）

### GET `/api/v1/conversations`

会话列表。**Response**：`items[]`：`conversationId`, `peer` UserBrief, `lastMessage`, `unreadCount`, `updatedAt`

### POST `/api/v1/conversations`

**Body**：`{ "peerUserId" }` → 单聊  
**Response 201**：`conversationId`

### GET `/api/v1/conversations/{id}/messages`

Query：`beforeId?`, `limit`  
**Response**：`messages[]`：`messageId`, `senderId`, `msgType`, `content`, `createdAt`

### POST `/api/v1/conversations/{id}/messages`

**Body**：`msgType`, `content`（JSON）  
**Response 201**

### WebSocket `WS /ws/im`

客户端 JSON：`{ "type": "send", "conversationId", "msgType", "content" }`  
服务端推送：`{ "type": "message", ... }`

---

## Search（P3）

### GET `/api/v1/search`

Query：`q`, `type`（`post` | `user` | `all`）, `cursor`, `limit`  
**Response**：`{ "posts": [], "users": [] }`

---

## Live（P4）

### POST `/api/v1/live/rooms`

**Body**：`title`, `coverMediaId?`  
**Response 201**：`{ "roomId" }`

### POST `/api/v1/live/rooms/{roomId}/start`

**Response 200**：`{ "pushUrl", "playUrlHls", "streamKey" }`

### POST `/api/v1/live/rooms/{roomId}/end`

**Response 204**

### GET `/api/v1/live/sessions`

正在直播列表 → `items[]`：`roomId`, `title`, `owner`, `playUrlHls`, `viewerCount`

---

## Reports（P5）

### POST `/api/v1/reports`

**Body**：`targetType`（post/user）, `targetId`, `reasonCode`, `detail?`  
**Response 201**：`{ "reportId" }`

---

## 错误码（示例，P0 定稿）

| code | HTTP | 说明 |
| --- | --- | --- |
| UNAUTHORIZED | 401 | 未登录 |
| FORBIDDEN | 403 | 无权限 |
| NOT_FOUND | 404 | |
| VALIDATION_ERROR | 400 | 字段校验 |
| RATE_LIMITED | 429 | 短信等 |
| CONFLICT | 409 | 重复关注等 |

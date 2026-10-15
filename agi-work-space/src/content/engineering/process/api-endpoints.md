---
title: API 接口明细
description: 接口路径与 JSON 字段（查字典用）
order: 5
---

REST 路径与请求/响应字段一览。共性规则见 [接口契约](/engineering/process/contracts)；落库见 [数据库设计](/engineering/tech/database-schema)、[数据库脚本](/engineering/tech/sql)。**改接口先改本页。**

P0 已实现：Auth（短信 mock 码 `888888`）、设密/找回密码、`GET/PATCH /users/me`、`GET /users/{pageId}`、Uploads presign/complete。P1 已实现：发帖、Feed、详情、软删、浏览、赞/藏、评论、分享链接、本人笔记列表 `GET /posts/mine`、主页笔记 `GET /users/{pageId}/posts`、本人收藏/点赞/浏览 `GET /users/me/favorites` `/likes` `/history`、改可见 `PATCH /posts/{id}/visibility`、作者改内容 `PATCH /posts/{id}`；评论点赞 POST 切换；收藏另有 DELETE 取消。P2 已实现：关注/取关、关注与粉丝列表、关注流按关注图筛选、通知收件箱、单聊文本/图片私信、分享笔记卡片到私信、获赞/被收藏列表、私信 `WS /ws/im`、评论 @。OAuth 未配 client 时返回配置缺失。

**契约有、代码还没有：** AI 润色/问答。P5 治理：社区举报、运营工单与用户冻结已落 moderation-service。登录注册图见 [登录注册流程](/engineering/tech/auth-flows)。

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
| 直播 / 举报 / 运营 | P4–P5 | `## Live` · `## Reports` · `## Ops` |

下面几个 JSON 形状会反复出现。

## 公共类型

### UserBrief（列表/卡片常用）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| userId | string | 内部 ID |
| pageId | string | 一页号 |
| nickname | string | |
| avatarUrl | string | 可空，CDN 或网关签名 URL |
| followedByMe | boolean | 关注/粉丝列表有；资料接口也有。未登录为 false |

### PostBrief

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| postId | string | |
| author | UserBrief | |
| title | string | 可空 |
| body | string | 摘要，最长约 400 字；纯文字卡片用 |
| coverUrl | string | 可空；视频帖可能是视频 URL，前端用 `<video>` 出首帧 |
| postType | string | text / image / video / mixed |
| category | string | 旅行/美食/时尚/摄影/生活/运动/游戏/音乐；旧帖可空 |
| original | boolean | 是否原创 |
| contentKind | string | 空 / 虚构创作 / 个人观点 / 专业内容 |
| eventId | string | 可空；活动目录 id（现 e1/e2） |
| eventTitle | string | 可空；发布时服务端快照 |
| visibility | string | `public` 公开 / `self` 仅自己 |
| likeCount | number | |
| favoriteCount | number | |
| commentCount | number | |
| viewCount | number | 浏览人数（`post_view`）；发现流暂为 0 |
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

**Query**：`audience` 可选。`ops` 时须在 `OPS_STAFF_USER_IDS` 内，否则 403 且不种 Cookie、不自动开户。  
**Body**：`phone`, `code`  
**Response 200**：`{ "userId", "pageId", "nickname", "avatarUrl" }`（并 Set-Cookie）

### POST `/api/v1/auth/register`

账密注册。**Body**：`phone?`, `email?`, `password`, `nickname?`  
`password`：8～32 位，须同时包含字母和数字。  
**Response 201**：同登录用户摘要 + Cookie

### POST `/api/v1/auth/login`

账密登录。**Query**：`audience` 可选，`ops` 时仅白名单员工。  
**Body**：`account`（手机或邮箱）, `password`  
**Response 200**：用户摘要 + Cookie。非员工运营登录 403，不种 Cookie。

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

### GET `/api/v1/users/me/suggest`

评论 @ 联想，必须登录。Query：`q`（可空；不要 `%` `_` `\`）。空词返回已关注（最多 8）。有词：一页号前缀或昵称包含，精确优先，最多 8，不含自己。  
**Response 200**：`UserSummary[]`（`userId`, `pageId`, `nickname`, `avatarUrl`）

### GET `/api/v1/users/{pageId}`

他人主页（公开字段 + stats）。GET 公开。路径先按一页号精确匹配；没有命中且昵称全站唯一时按昵称打开（给评论 @ 用）。前端随后跳到规范 `/u/{pageId}`。

### GET `/api/v1/users/{pageId}/posts`

Query：`cursor`, `limit` → `CursorPage<PostBrief>`。GET 公开。非作者只出 `visibility=public`；作者看自己（Cookie 对上）含仅自己可见。网关走 user-service，内部 Feign content `GET /internal/authors/{authorId}/posts`。

### GET `/api/v1/users/me/favorites` | `/likes` | `/history`

Query：`cursor`, `limit` → `CursorPage<PostBrief>`。必须登录。按收藏/点赞/最近浏览时间倒序。已删除或他人改为仅自己可见的帖不出现。网关走 user-service，内部 Feign content `GET /internal/users/{userId}/favorites` `|likes` `|history`。历史仅列表，尚未做单条删除/清空。

### GET `/api/v1/users/{pageId}/following` | `/followers`

Query：`cursor`, `limit` → `CursorPage<UserBrief>`。GET 公开；已登录时 `UserBrief.followedByMe` 表示当前用户是否已关注这一行。按关注时间倒序。

### GET `/api/v1/users/me/likes-received` | `/favorites-received`

必须登录，仅作者。Query：`cursor`, `limit`。  
**Response**：`CursorPage<ReceivedItem>`。`ReceivedItem`：`actor` UserBrief（含 `followedByMe`）、`postId`、`postTitle`、`createdAt`。按互动时间倒序。网关走 user-service，内部 Feign content `GET /internal/users/{userId}/likes-received` `|favorites-received`。他人主页点数字不调此接口。

### POST `/api/v1/users/{pageId}/follow`

必须登录。不能关注自己。新建 **201** `{ followed: true }`；已关注 **200** 同 body。计数写入 `user_stat`。新建关注会投递 `follow` 通知。

### DELETE `/api/v1/users/{pageId}/follow`

取消关注 **204**（未关注也 204）。

---

## Uploads（P0）

### POST `/api/v1/uploads/presign`

**Body**：`mimeType`, `sizeBytes`, `purpose`（`avatar` | `cover` | `post_image` | `post_video` | `im_image`）  
`im_image`：仅 jpeg/png/webp/gif，最大 10MB。  
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
| category | string | 是，须为首页 Tab 之一 |
| original | boolean | 否，默认 false |
| contentKind | string | 否；空或「不声明」表示不声明 |
| eventId | string | 否；须为服务端活动目录已有 id |
| visibility | string | 否；`public`（默认）或 `self`；`friend` 返回校验失败 |

**Response 201**：`{ "postId", "publishedAt" }`

Feed 只出 `visibility=public`。仅自己对非作者详情/赞评视为不存在。活动标题由服务端目录写入，不信任客户端。定时发布尚未接入。

### GET `/api/v1/posts/mine`

登录作者自己的已发布笔记（含仅自己可见）。Query：`cursor`, `limit` → `CursorPage<PostBrief>`（含 `viewCount`）。审核中/未通过尚未接入。

### PATCH `/api/v1/posts/{postId}`

作者改已发布笔记。Body 与发帖相同（title/body/mediaIds/postType/category/original/contentKind/eventId/visibility）。会整表替换媒体绑定。须登录且为作者。**Response 200**：`PostDetail`。`friend` 与定时仍校验失败。

### PATCH `/api/v1/posts/{postId}/visibility`

作者改可见范围。**Body**：`{ "visibility": "public" | "self" }`。**Response 200**：`PostBrief`。`friend` 校验失败。

### GET `/api/v1/posts/{postId}`

**Response 200**：详情含 `body`, `media[]`（url, mime, width/height 可选）, `author`, counts, `likedByMe`, `favoritedByMe`

### GET `/api/v1/feeds/following` | `/feeds/explore`

Query：`cursor`, `limit`，explore 另可 `category`（旅行/美食等）→ `CursorPage<PostBrief>`。GET 公开。关注流只出已关注作者的公开帖；未登录或未关注任何人为空页。cursor 形如 `{publishedAtMillis}_{postId}`。未带 category 的推荐流含全部；分类 Tab 只出对应 `post.category`。

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

### DELETE 同上 → 取消收藏

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

**Body**：`content`, `parentId?`, `mentionPageIds?`（@ 一页号或昵称）  
服务端还会从正文解析 `@token`。命中用户则投递 `mention` 通知（已收到本条评论/回复通知的人不再重复）。找不到的 token 忽略，评论仍成功。最多 10 人。  
**Response 201**：Comment

### DELETE `/api/v1/comments/{commentId}`

**Response 204**

---

## Share（P1–P2）

### POST `/api/v1/posts/{postId}/share/link`

**Response 200**：`{ "shareUrl" }`

### POST `/api/v1/posts/{postId}/share/im`

必须登录。**Body**：`{ "conversationId" }` 或 `{ "targetUserId" }`（有会话 id 优先）。  
**Response 201**：`{ "messageId" }`。content 校验笔记可见后 Feign message `POST /internal/im/share-card`，写入 `msgType=post_card`，`content`：`postId`, `title`, `coverUrl`, `authorNickname`。浏览器不能直接发 `post_card`。不能分享给自己。

---

## Notifications（P2）

### GET `/api/v1/notifications`

必须登录。Query：`cursor`, `limit`, `unreadOnly?`, `group?`（`comment` 评论和@ / `reaction` 点赞和收藏 / `follow` 新增关注）。  
**Response**：`CursorPage<NotificationItem>`

**NotificationItem**：`id`, `type`（`comment`/`reply`/`mention`/`like_post`/`favorite_post`/`like_comment`/`follow`）, `actor` UserBrief, `postId?`, `commentId?`, `preview?`（评论/回复/@ 正文摘要，最多 120 字）, `read`, `createdAt`。`mention` 归入 `group=comment`。

### POST `/api/v1/notifications/read`

**Body**：`{ "ids": [] }` 或 `{ "all": true }`  
**Response 204**

### GET `/api/v1/notifications/unread-count`

**Response**：`{ "count", "comment", "reaction", "follow" }` 各 Tab 未读；`count` 为合计。

---

## Conversations / IM（P2）

必须登录。

### GET `/api/v1/conversations`

会话列表。**Response**：`items[]`：`conversationId`, `peer` UserBrief, `lastMessage`（`messageId`, `msgType`, `text`, `createdAt`）, `unreadCount`, `updatedAt`

### POST `/api/v1/conversations`

**Body**：`{ "peerUserId" }` → 单聊。新建 **201** `{ conversationId }`；已有会话 **200** 同 body。不能给自己发。

### GET `/api/v1/conversations/unread-count`

**Response**：`{ "count" }` 各会话未读合计。

### GET `/api/v1/conversations/{id}/messages`

Query：`beforeId?`, `limit`  
**Response**：`{ "messages": [] }`：`messageId`, `senderId`, `msgType`, `content`, `createdAt`。打开最新一页会更新已读。

### POST `/api/v1/conversations/{id}/messages`

**Body 文本**：`{ "msgType": "text", "content": { "text": "..." } }`  
**Body 图片**：`{ "msgType": "image", "content": { "mediaId": "..." } }`。`mediaId` 须为本人已 `complete` 的 `purpose=im_image`。服务端写入 `content.url`，不信任客户端 URL。  
**Response 201** 消息。笔记卡片走 `POST /posts/{id}/share/im`，浏览器不能直接发 `post_card`。

### WebSocket `WS /ws/im`

必须登录。浏览器连同源 `/ws/im`（开发时 Vite 转到网关），握手带 `ONEPAGE_SESSION`；网关验 Session 后注入 `X-User-Id` 再 `lb:ws://onepage-message-service`。message-service **不解析 Cookie**。

落库仍走 REST `POST /conversations/{id}/messages`（或 WS 帧 `op=send`，字段同 REST body + `conversationId`）。提交成功后 Redis 频道 `onepage:im:push` 投到发送者与对端本机连接（多实例靠 Redis，不靠单机 Map）。

**服务端 → 浏览器 JSON**

| `op` | 字段 |
| --- | --- |
| `connected` | 握手成功 |
| `pong` | 回应 `ping` |
| `message` | `conversationId`，`payload` 同 REST 消息项（自己多端和对端都会收到） |
| `ack` | 仅 WS `send` 成功回给该连接；`conversationId` + `payload` |
| `error` | `code`，`detail` |

**浏览器 → 服务端 JSON**：`{"op":"ping"}`；`{"op":"send","conversationId","msgType":"text"|"image","content":...}`（字段同 REST）。

社区登录后壳内长连。消息页在线时不轮询；断线约 3 秒轮询 REST 作降级。发图走 presign `im_image` → complete → REST。

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

**Body**：`targetType`（post/user）, `targetId`, `reasonCode`（`spam` / `porn` / `abuse` / `illegal` / `other`）, `detail?`
**Response 201**：`{ "reportId" }`

社区用户提交。详情「更多 → 举报」、他人主页「举报」已接。处理走下面 Ops。

---

## Ops（P5，运营后台；`onepage-moderation-service` 8086）

员工 Session。非员工 403。名单 `OPS_STAFF_USER_IDS`（空则全部 403）。网关前缀 `/api/v1/ops/**` → moderation-service。运营站登录须带 `audience=ops`（DEC-035）。

### GET `/api/v1/ops/me`

已登录员工。**Response**：`UserSummary`。非员工或未配名单 403。运营壳进站先调这个，不要只看社区 Session。

### GET `/api/v1/ops/reports`

**Query**：`status` pending/done，`cursor`，`limit`

**Response**：`CursorPage<OpsReport>`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| reportId | string | |
| targetType | string | post / user |
| targetId | string | |
| targetTitle | string | 笔记标题或用户昵称，可空 |
| targetPageId | string | 被举报用户的一页号，笔记可空 |
| reasonCode | string | |
| detail | string | 可空 |
| status | string | pending / done |
| reporter | UserBrief | 可空 |
| handler | UserBrief | 已处理才有 |
| handleAction | string | dismiss / takedown / ban |
| handleNote | string | |
| createdAt | string | ISO-8601 |
| handledAt | string | 已处理才有 |

### PATCH `/api/v1/ops/reports/{reportId}`

**Body**：`action` dismiss / takedown / ban，`note?`

**Response**：`{ reportId, status }`。takedown 由 content 内部下架帖；ban 由 **auth** `status=1` 冻结（不是 user-service）。不能冻结员工或自己。

### GET `/api/v1/ops/users`

**Query**：`q` 一页号或昵称。**Response**：`OpsUser[]`（`userId` `pageId` `nickname` `avatarUrl` `frozen`）。

### POST `/api/v1/ops/users/{userId}/freeze`

### POST `/api/v1/ops/users/{userId}/unfreeze`

**Response**：`OpsUser`。解冻把 auth `status` 改回 0。

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

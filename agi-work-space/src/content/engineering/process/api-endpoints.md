---
title: API 接口明细
description: 接口路径与 JSON 字段（查字典用）
order: 5
---

REST 路径与请求/响应字段一览。共性规则见 [接口契约](/engineering/process/contracts)；落库见 [数据库设计](/engineering/tech/database-schema)、[数据库脚本](/engineering/tech/sql)。**改接口先改本页。**

P0 已实现：Auth（短信 mock 码 `888888`）、设密/找回密码、`GET/PATCH /users/me`、`GET /users/{pageId}`、Uploads presign/complete。P1 已实现：发帖（含草稿/好友可见/定时）、Feed、详情、软删、浏览、赞/藏、评论树与评赞、分享链接、本人笔记列表 `GET /posts/mine`、主页笔记 `GET /users/{pageId}/posts`、本人收藏/点赞/浏览 `GET /users/me/favorites` `/likes` `/history`（可删/清空）、改可见 `PATCH /posts/{id}/visibility`、作者改内容 `PATCH /posts/{id}`；评论点赞 POST 切换；收藏另有 DELETE 取消。创作者报表 `GET /creator/stats`（content 聚合观看/赞评藏，Feign user 涨粉）。P2 已实现：关注/取关、关注与粉丝列表、关注流按关注图筛选、通知收件箱、单聊文本/图片私信、分享笔记卡片到私信、获赞/被收藏列表、私信 `WS /ws/im`、评论 @。OAuth 未配 client 时返回配置缺失。

P3 已实现：搜索笔记/用户/话题/在播（ES；筛选进查询）；AI 润色 `POST /ai/posts/polish`、耶耶 `POST /ai/chat` 与 `POST /ai/chat/stream`（`onepage-ai-service`，HTTP Chat Completions）。P4 已实现：`onepage-live-service` 开播/列表/HLS 地址、直播间文字聊天、分享 `live_card`、本场公告、OBS 断流自动关播、创作者 `GET /live/stats`。P5 治理：社区举报、运营工单/用户/笔记审稿/评论/目录 CRUD、私信巡查、媒体库、用户关注粉丝与收件箱已落 moderation-service（隐藏走 Feign message；媒体与审稿走 Feign content；关注走 Feign user；通知走 Feign notification）。登录注册图见 [登录注册流程](/engineering/tech/auth-flows)。

## 按模块查（用编辑器搜索标题）

| 模块 | 阶段 | 文内标题 |
| --- | --- | --- |
| 登录注册 | P0 | `## Auth` |
| 用户与关注 | P0–P2 | `## Users` |
| 上传 | P0 | `## Uploads` |
| 笔记 | P1 | `## Posts` |
| 创作者报表 | P1 | `## Creator` |
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
**Response 200**：`{ "userId", "pageId", "nickname", "avatarUrl" }`（并 Set-Cookie）。`audience=ops` 种运营 Cookie。

### POST `/api/v1/auth/register`

账密注册。**Body**：`phone?`, `email?`, `password`, `nickname?`  
`password`：8～32 位，须同时包含字母和数字。  
**Response 201**：同登录用户摘要 + Cookie

### POST `/api/v1/auth/login`

账密登录。**Query**：`audience` 可选，`ops` 时仅白名单员工。  
**Body**：`account`（手机或邮箱）, `password`  
**Response 200**：用户摘要 + Cookie。`audience=ops` 种 **`ONEPAGE_OPS_SESSION`**，不改 C 端 `ONEPAGE_SESSION`。非员工运营登录 403，不种 Cookie。

### POST `/api/v1/auth/logout`

**Query**：`audience` 可选，`ops` 时只清运营 Session / `ONEPAGE_OPS_SESSION`。  
**Response 200** `{ "ok": true }`。由网关清 Redis Session（不要再转发 auth invalidate，否则请求会挂住）。运营站也可带头 `X-OnePage-Audience: ops`。

### GET `/api/v1/auth/account`

已登录。**Response 200** `{ "phone", "email", "hasPassword", "githubBound", "googleBound" }`（空字符串表示未绑手机/邮箱）

### POST `/api/v1/auth/password`

已登录设密 / 改密。**Body**：`password`（必填，规则同注册）；已有密码时 `oldPassword` 必填。  
**Response 200**：同 `GET /account`

### POST `/api/v1/auth/password/reset`

未登录找回。**Body**：`phone`, `code`（`scene=reset` 的验证码）, `password`  
该手机须已有账号。成功后种 Cookie 并登录。

### GET `/api/v1/auth/oauth/{provider}/authorize`

`provider`: `github` | `google`。**Query** `intent` 可选：`bind` 表示已登录绑定（须带 Session，网关公开路径仍会注入 `X-User-Id`）。302 到 IdP，`state` 存 Redis 10 分钟。`intent=bind` 且未登录：302 `/?oauth=need_login`。

### GET `/api/v1/auth/oauth/{provider}/callback`

OAuth 回调。有 `code`：换票后取对方 **id、name、email**（GitHub 无公开邮箱再打 `/user/emails`）。`state` 为 bind：绑到当前用户，不新开号；对方 id 已绑别人则 302 `/settings?oauth=already_bound`，成功 `/settings?oauth=bind_ok`。登录：已有该平台绑定则进原号；**邮箱已被占用则不开新号**，302 `/?oauth=email_taken`；否则建号（name 作昵称，email 写入账号）、种 Cookie、302 首页。无 `code` 或带 `error`：不 500，直接 302 回首页。不落头像 URL、不改表、不静默按邮箱并号。

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

**Body**（均可选）：`nickname`, `bio`, `gender`, `avatarMediaId`, `coverMediaId`。**不接受 `pageId`**（一页号注册后不可改）。  
**Response 200**：完整 profile。正在开播（`GET /live/mine` 有场次）则 **409**「直播中不可修改资料，请先结束直播」。user Feign live `GET /internal/live/users/{userId}/active`；直播服务不可用时不拦。

### GET `/api/v1/users/me/suggest`

评论 @ 联想，必须登录。Query：`q`（可空；不要 `%` `_` `\`）。空词返回已关注（最多 8）。有词：一页号前缀或昵称包含，精确优先，最多 8，不含自己。  
**Response 200**：`UserSummary[]`（`userId`, `pageId`, `nickname`, `avatarUrl`）

### GET `/api/v1/users/{pageId}`

他人主页（公开字段 + stats）。GET 公开。路径先按一页号精确匹配；没有命中且昵称全站唯一时按昵称打开（给评论 @ 用）。前端随后跳到规范 `/u/{pageId}`。

### GET `/api/v1/users/{pageId}/posts`

Query：`cursor`, `limit` → `CursorPage<PostBrief>`。GET 公开。非作者只出 `visibility=public`；作者看自己（Cookie 对上）含仅自己可见。网关走 user-service，内部 Feign content `GET /internal/authors/{authorId}/posts`。

### GET `/api/v1/users/me/favorites` | `/likes` | `/history`

Query：`cursor`, `limit` → `CursorPage<PostBrief>`。必须登录。按收藏/点赞/最近浏览时间倒序。已删除或他人改为仅自己可见的帖不出现。网关走 user-service，内部 Feign content `GET /internal/users/{userId}/favorites` `|likes` `|history`。

### DELETE `/api/v1/users/me/history/{postId}`

必须登录。删本人一条浏览记录。**204**。内部 Feign `DELETE /internal/users/{userId}/history/{postId}`。

### DELETE `/api/v1/users/me/history`

必须登录。清空本人浏览记录。**204**。内部 Feign `DELETE /internal/users/{userId}/history`。

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
**Response 200**：`{ "mediaId", "status": "ready", "url" }`。`url` 为 `MEDIA_PUBLIC_BASE` + objectKey，运营活动封面等可直接写入。

浏览器读文件用返回的 `url`（MinIO），不要调 `GET /api/v1/media/{id}`：公网没有这条。归属核验走内网 `GET /internal/media/{id}`。网关也不再转发 `/api/v1/media/**`。

---

## Posts（P1）

### POST `/api/v1/posts`

创建笔记。**Body**

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| title | string | 否 |
| body | string | 正式发布是；草稿可空 |
| mediaIds | string[] | 否；正式发图/视频必须有 |
| postType | string | 是 |
| category | string | 正式发布是，须为首页 Tab 之一；草稿可空 |
| original | boolean | 否，默认 false |
| contentKind | string | 否；空或「不声明」表示不声明 |
| eventId | string | 否；须为服务端活动目录已有 id |
| visibility | string | 否；`public`（默认）/ `self` / `friend` |
| draft | boolean | 否；`true` 则 `status=draft`，仅作者可见 |
| scheduledAt | string | 否；ISO 本地时间（`yyyy-MM-ddTHH:mm`）；写入 `published_at`，未到点不进 Feed |

**Response 201**：`{ "postId", "publishedAt", "reviewStatus" }`（`draft` / `pending` / `published`）。公开笔记先待审，不进 Feed；仅自己与好友可见直接 `published`。好友可见出现在关注者的关注流，不进发现流。

Feed 只出 `status=published`、`published_at <= now`：发现流 `visibility=public`；关注流公开 + 好友可见。仅自己对非作者详情/赞评视为不存在。活动标题由服务端目录写入，不信任客户端。分类须为启用中的 `post_category.name`（`GET /catalog`）。

### GET `/api/v1/catalog`

公开。社区首页 Tab、发帖分类/活动、创作者活动中心同源。

**Response**：`{ categories: OpsCategory[], campaigns: OpsCampaign[], topics: OpsTopic[] }`（仅 `enabled=true`）。活动 `status`：`live` / `upcoming` / `ended`。

### GET `/api/v1/catalog/topics`

公开。Query：`q` 可选。发帖联想启用中的话题名。搜索页话题 Tab 走 `GET /search?type=topic`。

### GET `/api/v1/posts/mine`

登录作者自己的笔记（草稿 / 已发布 / 审核中 / 未通过，含仅自己与好友可见）。Query：`cursor`, `limit` → `CursorPage<PostBrief>`（含 `viewCount`、`reviewStatus`、`rejectReason`）。

### PATCH `/api/v1/posts/{postId}`

作者改草稿、已发布、待审或未通过笔记。Body 与发帖相同（另可 `draft`、`scheduledAt`）。会整表替换媒体绑定。未通过再保存会回到待审。草稿带 `draft=true` 仍为草稿。须登录且为作者。**Response 200**：`PostDetail`（含 `reviewStatus`）。

### PATCH `/api/v1/posts/{postId}/visibility`

作者改可见范围。**Body**：`{ "visibility": "public" | "self" | "friend" }`。**Response 200**：`PostBrief`。从仅自己/好友改为公开会重新待审。

### GET `/api/v1/posts/{postId}`

**Response 200**：详情含 `body`, `media[]`（url, mime, width/height 可选）, `author`, counts, `likedByMe`, `favoritedByMe`，`reviewStatus`，`rejectReason`。待审/未通过仅作者可看。

### GET `/api/v1/feeds/following` | `/feeds/explore`

Query：`cursor`, `limit`，explore 另可 `category`（旅行/美食等）→ `CursorPage<PostBrief>`。GET 公开。关注流只出已关注作者的公开或好友可见帖（`published_at` 已到）；未登录由社区前端弹登录，接口仍可返回空页。cursor 形如 `{publishedAtMillis}_{postId}`。未带 category 的推荐流含全部公开帖；分类 Tab 只出对应 `post.category`。

### DELETE `/api/v1/posts/{postId}`

作者软删 **204**

### POST `/api/v1/posts/{postId}/view`

记录浏览历史 **204**（登录用户）

### POST `/api/v1/posts/{postId}/play-complete`

须登录。视频播到约 90% 记一次完播（`post_play_complete`，同一人同一篇只一行）。非视频 **204** 不写。**Response 204**。

---

## Creator（创作者报表）

### GET `/api/v1/creator/stats`

须登录。Query：`range` = `1`（昨日）/ `7`（近 7 日，含今天）/ `30`（近 30 日，含今天）。日历按 `Asia/Shanghai`。环比对照等长前一段。`onepage-content-service`；粉丝段 Feign user。

**Response 200**：`{ "range", "from", "to", "account", "content", "fans", "pending" }`

`account`：`impressions` / `views` / `likes` / `comments` / `favorites` / `shares` / `followerNet` / `followerNew` / `coverCtr` / `videoCompleteRate` 均为 `{ value, delta }`（`delta` 为小数，无对照为 `null`）。`coverCtr` / `videoCompleteRate` 的 `value` 是百分数 0–100。`mix` `{ image, video, article }`（周期内观看按体裁）；`viewTrend` `[{ date, value }]`。口径：曝光 = Feed 出卡 `post_cover_show`；观看 = `post_view` 周期内更新过的 (用户,笔记)；封面点击率 = 观看/曝光；完播率 = 完播/视频观看；赞/评/藏/分享按事件时间；净涨粉 = 周期内新增关注（取关删边，无法回放）。

`pending`：`{ draft, review }` 当前草稿数、审核中数（全表，不限周期）。

`content`：`avgViews` `avgLikes` `avgComments` `postCount`；`posts` 最多 200 篇已发布 `{ postId, title, postType, views, likes, comments, favorites }`（周期内计数）。

`fans`：`followerTotal` / `followerNet` / `followerNew` 同上 Metric；`unfollow` `null`；`activeRate` 周期内互动粉丝 / 当前粉丝；`reciprocalRate` 互关占当前粉丝；`viewerFans` `engagedFans`；`retention7d` `null`；`change` `{ followNew, unfollow, net }`；`trend` 每日新增关注。

内网：`GET /internal/users/{userId}/creator-fan-stats?from&to&prevFrom&prevTo`；`POST /internal/users/{userId}/count-in-followers` body 为 userId 数组。

### GET `/api/v1/live/stats`

须登录。Query 同 `range`。`onepage-live-service`。

**Response 200**：`{ "range", "sessions", "viewers", "peakOnline", "followerNew", "gifters" }` 均为 Metric（`gifters` 恒 0，礼物未做）。场次按 `started_at` 且 `live|ended`；观看人次 = 这些场次 `live_chat` 里 `join` 的去重用户。`peakOnline` 为场次 `peak_viewer_count` 的最大值（心跳在场人数）。`followerNew` 为开播时段内新增关注。

---

## AI（P1 美化 / P3 问答）

润色/问答须登录。服务 `onepage-ai-service`（8088）。不引入 LangChain4j / Spring AI。密钥默认环境变量，运营可改 Redis。

### GET `/api/v1/ai/bootstrap`

公开。**Response 200**：`{ "yehPreset", "yehQuestions": [] }`。给耶耶首页默认文案和联想。

### POST `/api/v1/ai/posts/polish`

**Body**：`{ "title?", "body", "mode?" }`。`mode=optimize`（缺省）优化标题和正文；`mode=continue` 续写正文、标题不变。  
**Response 200**：`{ "title?", "body" }`  
没配密钥或上游失败 **503** `CONFIG_MISSING`（「暂时不能润色」）；原标题正文可继续发布。

### POST `/api/v1/ai/chat`

**Body**：`{ "question", "sessionId?" }`（`sessionId` 仅前端会话，服务端不落库）  
**Response 200**：`{ "answer", "citations": [{ "postId", "snippet", "title", "coverUrl", "nickname", "pageId" }] }`  
先 `GET /search?type=all` 再生成（笔记 + 话题进上下文；引用胶囊仍只对笔记）。笔记与话题都无命中：`answer` 为「暂时不能根据站内笔记回答」。无密钥：仍 200，「暂时不能回答」，不查 ES。温度 / `max_tokens` 读运营配置；本接口始终整段返回。模型须归纳并标 `[1]`，禁止复述材料全文。

### POST `/api/v1/ai/chat/stream`

登录。**Body** 同 `/ai/chat`。**Response** `text/event-stream`：过程 `event: stage` 的 `data` 为 `{ "s": "retrieve"|"notes"|"write", "n" }`（`n` 为命中条数）；`event: delta` 的 `data` 为 `{ "t" }`；结束 `event: done` 的 `data` 为 `{ "citations": [{ "postId", "snippet", "title", "coverUrl", "nickname", "pageId" }] }`；失败 `event: error` 的 `data` 为 `{ "message" }`。运营打开流式则上游 `stream:true` 按片段推 `delta`；关闭则一次生成后只推一条 `delta`。ai-service 读上游 SSE 用 RestClient `exchange` 原样读流，不要 `retrieve().body(InputStream)`（没有 `text/event-stream` 转换器）。检索逻辑与 `/ai/chat` 相同。润色不走本接口。

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

社区详情用 POST 切换。**Response 200** `{ "liked", "likeCount" }`

### DELETE `/api/v1/reactions/comments/{commentId}/like`

取消评论赞。未赞过也是 200 `{ "liked": false, "likeCount" }`。

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

必须登录。Query：`cursor`, `limit`, `unreadOnly?`, `group?`（`comment` 评论和@ / `reaction` 点赞和收藏 / `follow` 新增关注 / `review` 审核）。  
**Response**：`CursorPage<NotificationItem>`

**NotificationItem**：`id`, `type`（`comment`/`reply`/`mention`/`like_post`/`favorite_post`/`like_comment`/`follow`/`review_reject`）, `actor` UserBrief, `postId?`, `commentId?`, `preview?`（评论/回复/@ 正文摘要或审稿回复，最多 120 字）, `read`, `createdAt`。`mention` 归入 `group=comment`；`review_reject` 归入 `group=review`。社区通知页拉列表不自动已读，点开条目再 `POST /notifications/read`。

### POST `/api/v1/notifications/read`

**Body**：`{ "ids": [] }` 或 `{ "all": true }`  
**Response 204**

### GET `/api/v1/notifications/unread-count`

**Response**：`{ "count", "comment", "reaction", "follow", "review" }` 各 Tab 未读；`count` 为合计。

---

## Conversations / IM（P2）

必须登录。

### GET `/api/v1/conversations`

会话列表。**Response**：`items[]`：`conversationId`, `peer` UserBrief, `lastMessage`（`messageId`, `msgType`, `text`, `createdAt`）, `unreadCount`, `updatedAt`。`text` 为预览：正文、`[图片]`、`[笔记] …`。打开会话拉消息时服务端 `markRead`。

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
**Response 201** 消息。笔记卡片走 `POST /posts/{id}/share/im`，直播卡片走 `POST /live/sessions/{id}/share/im`，浏览器不能直接发 `post_card` / `live_card`。

### WebSocket `WS /ws/im`

必须登录。浏览器连同源 `/ws/im`（开发时 Vite 转到网关），握手带 C 端 `ONEPAGE_SESSION`；网关验 Session 后注入 `X-User-Id` 再 `lb:ws://onepage-message-service`。message-service **不解析 Cookie**。

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

公开。Query：`q`（空则空结果）、`type`（`post` | `user` | `all` | `topic` | `live`）、`cursor`、`limit`、`postType?`（逗号分隔 `image`/`video`）、`sort?`（`latest`/`hot`/`likes`/`comments`/`favorites`）、`time?`（`week`/`month`/`half`）、`scope?`（`following`/`seen`/`unseen`，须登录，靠 `X-User-Id`）  
**Response**：`{ "posts": PostBrief[], "users": [{ "userId", "pageId", "nickname", "avatarUrl", "bio" }], "topics": [{ "topicId", "name", "official", "sortOrder" }], "lives": LiveItem[], "nextCursor", "hasMore" }`  
笔记/用户/话题/在播走 ES（话题 `onepage-topics` 只含启用；在播 `onepage-lives` 只含 `status=live`）。筛选只作用于 `type=post`。`scope=following` Feign 关注 id；`seen`/`unseen` Feign 浏览 id。发帖联想仍 `GET /catalog/topics`。顶栏空词直播仍 `GET /live/sessions`。MQ 未消费时由 content/user/live Feign 写入，search 启动回填（含话题与在播）。

---

## Live（P4）

### POST `/api/v1/live/rooms`

须登录。**Body**：`title`, `category?`, `coverMediaId?`（可选；须是本人已 `complete` 的图片，`purpose=cover`）, `notice?`（本场公告，最多 200 字；空则观众侧用默认欢迎文案）  
**Response 201**：`{ "roomId" }`  
`onepage-live-service`（8089）Feign content 核媒体后把 `objectKey` 写入 `live_room.cover_object_key`。同一主播同时只能一场 `live`。

### POST `/api/v1/live/rooms/{roomId}/start`

须登录且是房主。**Response 200**：`{ "sessionId", "roomId", "title", "coverUrl", "notice", "pushUrl", "playUrlHls", "streamKey" }`  
`coverUrl` 为 `{MEDIA_PUBLIC_BASE}/{objectKey}`，无封面为空串。推流/HLS 前缀来自 `LIVE_PUSH_BASE` / `LIVE_HLS_BASE`，本服务不内嵌媒体服务器。`playUrlHls` 为 `{LIVE_HLS_BASE}/{streamKey}/index.m3u8`（MediaMTX）。

### POST `/api/v1/live/rooms/{roomId}/end`

须登录且是房主。**Response 204**。关播后场次 `status=ended`，立刻从列表消失。OBS 停推：live 轮询 MediaMTX API 或 HLS，见过推流后再断，宽限 `LIVE_UNPUBLISH_GRACE_SECONDS`（默认 15）关播；期内再推取消。未推过流不关。内网 hooks 可选。

### GET `/api/v1/live/mine`

须登录。本人若有 `status=live` 场次，**Response 200** 同 start（含 `title`、`coverUrl`、`notice`、`pushUrl`、`streamKey`）；没有则 **204**。创作者开播台刷新后恢复 OBS 说明。

创作者直播总览见 [GET `/api/v1/live/stats`](#get-apiv1livestats)（写在 Creator 节）。

### POST `/api/v1/live/rooms/{roomId}/notice`

须登录且是房主。**Body**：`{ "notice" }`（最多 200 字，可空）  
**Response 200**：`{ "notice" }`。写入 `live_room.notice`；观众靠场次轮询看到。

### GET `/api/v1/live/sessions`

公开。Query：`q?`, `category?`  
**Response**：`{ "items": [{ "sessionId", "roomId", "title", "category", "status", "coverUrl", "notice", "playUrlHls", "viewerCount", "owner", "startedAt" }] }`  
只出 `status=live`。`coverUrl` 有封面才非空。在线人数 Redis ZSET `live:presence:{sessionId}`（心跳 25s 内成员数）。

### GET `/api/v1/live/sessions/{sessionId}`

公开。**Response** 同列表单项（含已结束场次的 `status=ended`）。`peek=true` 仍只读，不再 +1 人数。人数来自在场 ZSET。

### GET `/api/v1/live/sessions/{sessionId}/messages`

公开。Query：`after?`（上一批最大 `messageId`）。无 `after` 返回该场最近 50 条按时间正序；有则返回更大 id 最多 50 条。  
**Response**：`{ "items": [{ "messageId", "kind", "body", "createdAt", "author" }] }`  
`kind` 为 `text` 或 `join`。表 `live_chat`，不与私信混。

### POST `/api/v1/live/sessions/{sessionId}/heartbeat`

公开（有 Cookie 仍注入 `X-User-Id`）。**Body**：`{ "presenceId" }`（游客必填，8～64 字母数字或 `-`；已登录用 `u:{userId}`）。刷新 ZSET 分数，清掉超过 25s 未心跳的成员。**Response 200**：`{ "status", "viewerCount", "join" }`。`join` 仅登录观众**首次**进房（25s 内再心跳不再发）；房主进房不计「来了」。关播 409。

### POST `/api/v1/live/sessions/{sessionId}/messages`

须登录。**Body**：`{ "body" }`（1～200 字，trim）  
**Response 201**：一条 ChatLine。场次已 `ended` 则 409。画面弹幕 overlay 不做。

### POST `/api/v1/live/sessions/{sessionId}/share/link`

须登录。**Response 200**：`{ "shareUrl" }`（社区 `/live/{sessionId}`）。场次不存在 404。

### POST `/api/v1/live/sessions/{sessionId}/share/im`

须登录。**Body**：`{ conversationId? , targetUserId? }`（二选一）  
**Response 201**：`{ "messageId" }`。live 编排快照后 Feign message `POST /internal/im/share-live-card`，写入 `msgType=live_card`，`content`：`sessionId`, `title`, `coverUrl`, `authorNickname`。浏览器不能直接发 `live_card`。不能分享给自己。

### GET `/internal/live/search`

内网。Header `X-Internal-Token`。**Response**：`{ "items": LiveItem[] }`（当前 `status=live`，无关键词过滤）。search 回填 `onepage-lives` 用。

### GET `/internal/live/sessions/briefs` · `/sessions/{sessionId}`

内网。Header `X-Internal-Token`。briefs Query `ids`。**Response**：`LiveItem[]` / 单条 `LiveItem`（含已结束）。治理工单标题与预览用。

### POST `/internal/live/sessions/{sessionId}/end`

内网。Header `X-Internal-Token`。运营关播，不校验房主。已结束则空操作。**Response 204**。

### POST `/internal/live/hooks/publish` · `/unpublish`

内网、**可选**。Header `X-Internal-Token`。主路径是 live 每 2s 轮询推流；钩子只是更快。MediaMTX 可直连 `:8089`。Query 或 JSON：`path`（取最后一段当 `stream_key`）或 `streamKey`。**Response 204**。

---

## Reports（P5）

### POST `/api/v1/reports`

**Body**：`targetType`（`post` / `user` / `comment` / `live`）, `targetId`, `reasonCode`（`spam` / `porn` / `abuse` / `illegal` / `other`）, `detail?`
**Response 201**：`{ "reportId" }`

社区用户提交。详情「更多 → 举报」、评论「举报」、他人主页「举报」、直播间资料卡三角「举报这场直播」（`live` + sessionId）已接。处理走下面 Ops。评论工单在治理台显示「删除评论 / 封禁用户」。直播工单显示「关播 / 封禁主播」，关播走内网 `POST /internal/live/sessions/{sessionId}/end`。

---

## Ops（P5，运营后台；`onepage-moderation-service` 8086）

员工 Session（`ONEPAGE_OPS_SESSION`）。非员工 403。名单 `OPS_STAFF_USER_IDS`（空则全部 403）。网关前缀 `/api/v1/ops/**` → moderation-service。运营站登录须带 `audience=ops`（DEC-035 / DEC-046）。

### GET `/api/v1/ops/me`

已登录员工。**Response**：`UserSummary`。非员工或未配名单 403。运营壳进站先调这个，不要只看社区 Session。

### GET `/api/v1/ops/reports`

**Query**：`status` pending/done，`cursor`，`limit`

**Response**：`CursorPage<OpsReport>`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| reportId | string | |
| targetType | string | post / user / comment / live |
| targetId | string | |
| targetTitle | string | 笔记标题或用户昵称，可空 |
| targetPageId | string | 作者或被举报用户的一页号，可空 |
| authorId | string | 笔记作者，用户工单为空 |
| takenDown | boolean | 笔记已下架 / 评论已隐藏 / 直播已结束 |
| reasonCode | string | |
| detail | string | 可空 |
| status | string | pending / done |
| reporter | UserBrief | 可空 |
| handler | UserBrief | 已处理才有 |
| handleAction | string | dismiss / takedown / restore / ban / unban |
| handleNote | string | |
| createdAt | string | ISO-8601 |
| handledAt | string | 已处理才有 |

### PATCH `/api/v1/ops/reports/{reportId}`

**Body**：`action` dismiss / takedown / restore / ban / unban，`note?`

**Response**：`{ reportId, status }`。笔记工单：takedown 下架、restore 恢复、ban 封禁作者（同时下架）。评论工单：takedown 隐藏评论、restore 恢复评论、ban 封禁评论作者（同时隐藏该评）。直播工单：takedown 关播、ban 关播并封禁主播、unban 解冻主播；**不能 restore**。用户工单：ban / unban 冻结/解冻该用户。dismiss 忽略。不能冻结员工或自己。已处理工单仍可 restore / ban / unban / takedown（直播 restore 除外），dismiss 不行。

### GET `/api/v1/ops/lives/{sessionId}`

员工预览直播工单对象。已结束也返回标题、封面、主播、公告。**Response**：`OpsLiveView`（`ended` 为 true 表示已关播）。

### GET `/api/v1/ops/posts`

**Query**：`q` 标题或笔记 ID，`taken` 空=全部 / pending 待审 / live 已通过在线 / rejected 未通过 / down 已下架，`cursor`，`limit`  
**Response**：`CursorPage<OpsPostRow>`（含 `takenDown` `reviewStatus` `rejectReason`）。

### GET `/api/v1/ops/posts/{postId}`

员工预览。已下架/待审也返回正文和媒体。**Response**：`OpsPostView`（含 `takenDown`、`reviewStatus`、`rejectReason`、`media[]`）。

### POST `/api/v1/ops/posts/{postId}/approve`

### POST `/api/v1/ops/posts/{postId}/reject`

`reject` Query：`reason` 必填，最长 200；`handlerId` 内网由运营传入。待审或未可通过；通过后才进 Feed。驳回后投递 `review_reject`。内网 Feign **POST**。

### POST `/api/v1/ops/posts/{postId}/takedown`

### POST `/api/v1/ops/posts/{postId}/restore`

**Response**：`OpsPostView`。不经过举报工单，直接处置笔记。恢复后为已通过，不再排队。

### GET `/api/v1/ops/comments`

**Query**：`q` 正文或评论/笔记 ID，`hidden` yes/no，`cursor`，`limit`  
**Response**：`CursorPage<OpsCommentRow>`。

### POST `/api/v1/ops/comments/{commentId}/hide`

### POST `/api/v1/ops/comments/{commentId}/restore`

**Response 204**。隐藏后社区评论列表不再出现（`comment.status=1`）。

### GET `/api/v1/ops/overview`

**Response**：`communityUsers` `postsLive` `postsDown` `comments` `reportsPending` `reportsDone` `categories` `campaigns` `topics`。

### GET `/api/v1/ops/catalog`

含停用项。**Response**：`{ categories, campaigns, topics }`（完整字段，非只读目录名）。

### POST `/api/v1/ops/categories` · PATCH `/api/v1/ops/categories/{id}` · POST `.../enabled?value=`

### POST `/api/v1/ops/campaigns` · PATCH `/api/v1/ops/campaigns/{eventId}` · POST `.../enabled?value=`

Body 含 `eventId`（新建）`title` `intro` `coverUrl` `status` `sortOrder`。`coverUrl` 只存公开对象地址，不要带预签名 query。治理调 content 内网改活动用 **POST** `/internal/campaigns/{eventId}`（Feign 默认客户端不能 PATCH）。

### POST `/api/v1/ops/topics` · PATCH `/api/v1/ops/topics/{id}` · POST `.../enabled?value=`

`official` 官方话题。用户发帖 `#标签` 也会写入 topic 表。

### GET `/api/v1/ops/users/{userId}`

**Response**：`OpsUser`。

### GET `/api/v1/ops/users`

**Query**：`q` 可空（空则全表），`cursor`，`limit`。**Response**：`CursorPage<OpsUser>`。

### POST `/api/v1/ops/users/{userId}/freeze`

### POST `/api/v1/ops/users/{userId}/unfreeze`

**Response**：`OpsUser`。解冻把 auth `status` 改回 0。

### GET `/api/v1/ops/users/{userId}/following`

### GET `/api/v1/ops/users/{userId}/followers`

**Query**：`cursor`，`limit`。**Response**：`CursorPage<UserBrief>`。内网 `GET /internal/users/{userId}/ops/following|followers`。

### POST `/api/v1/ops/users/{userId}/following/{peerId}/remove`

`userId` 是关注者，`peerId` 是被关注者。**Response 204**。删 `user_follow` 一对并回写 `user_stat` 计数。

### POST `/api/v1/ops/users/{userId}/followers/{peerId}/remove`

`userId` 是被关注者，`peerId` 是粉丝。**Response 204**。同一套拆边。

### GET `/api/v1/ops/users/{userId}/notifications`

**Query**：`group` 可空（comment / reaction / follow，与用户收件箱分组相同），`cursor`，`limit`。  
**Response**：`CursorPage<NotificationItem>`。不把条目标已读。内网 `GET /internal/notifications/ops?userId=`。

### POST `/api/v1/ops/notifications/{id}/hide`

**Response 204**。物理删除 `notification` 行。内网 Feign **POST**，不要 PATCH。

### GET `/api/v1/ops/media`

**Query**：`q` 媒体 ID 或上传人 ID（须数字），`purpose` avatar/cover/post_image/post_video/im_image，`status` pending/ready/fail，`hidden` yes/no，`cursor`（上一页最小 mediaId），`limit`。  
**Response**：`CursorPage<OpsMediaRow>`：`mediaId` `owner` `purpose` `mimeType` `sizeBytes` `status` `url`（仅 ready，运营仍可看已隐藏）`postId` `hidden` `createdAt`。

### POST `/api/v1/ops/media/{mediaId}/hide`

### POST `/api/v1/ops/media/{mediaId}/restore`

**Response 204**。隐藏写 `media_asset.blocked_at`，社区笔记不再出该图；头像/封面会清资料 object key。不删 MinIO。已有库须执行 `006_ops_media_block.sql`。已发出的私信图请 `POST /ops/messages/{id}/hide`。

### GET `/api/v1/ops/conversations`

**Query**：`q` 可空（用户 ID 或会话 ID，须为数字），`cursor`（`epochMilli_conversationId`），`limit`。  
**Response**：`CursorPage<OpsConversationRow>`：`conversationId` `left` `right`（`OpsImPeer`：userId/pageId/nickname/avatarUrl）`preview` `updatedAt`。

### GET `/api/v1/ops/conversations/{conversationId}/messages`

**Query**：`hidden` yes/no（可空=全部含已隐藏），`cursor`（上一页最小 messageId），`limit`。  
**Response**：`CursorPage<OpsImMessageRow>`：`messageId` `conversationId` `sender` `msgType` `preview` `imageUrl` `postId` `hidden` `createdAt`。

### POST `/api/v1/ops/messages/{messageId}/hide`

### POST `/api/v1/ops/messages/{messageId}/restore`

**Response 204**。隐藏写 `message.deleted_at`，社区会话/未读不再出现该条。内网 Feign **POST**，不要 PATCH。已有库须执行 `002_ops_hide.sql`。

### GET `/api/v1/ops/ai`

员工。**Response**：`llmBaseUrl` `llmModel` `llmKeySet` `llmKeyMasked` `yehPreset` `yehQuestions` `chatEnabled` `polishEnabled` `persisted` `chatSystem` `polishSystem` `continueSystem` `retrieveLimit` `contextBodyMax` `temperature` `maxTokens` `streamEnabled`。密钥不回全文。`persisted=false` 表示尚未写入 Redis，当前用环境变量。三段 prompt 是 system 文案。`retrieveLimit`/`contextBodyMax` 是耶耶 RAG：检索条数与注入正文截断。`temperature` 默认 0.4、范围 0–2；`maxTokens` 0 表示不设上限（上限 8192）；`streamEnabled` 只影响耶耶 SSE。

### POST `/api/v1/ops/ai`

员工。**Body** 可只传本页字段（模型 / 耶耶 / 发帖），未传的保持原值。另可 `llmApiKey?` `clearLlmKey?`。密钥留空不改；`clearLlmKey=true` 清空已存密钥。联想最多 6 条，空列表回落到内置三条。Feign 写 Redis `onepage:ai:settings`。

### POST `/api/v1/ops/ai/reset`

员工。删 Redis 配置，回到环境变量默认。**Response** 同 GET。

### POST `/api/v1/ops/ai/probe`

员工。用表单里的地址/模型探测（密钥留空则用已保存的）。**Response**：`{ "ok", "message" }`。连不上也是 200 + `ok=false`，不把上游错误原文甩给浏览器。

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

---
title: 数据库设计
description: 各服务 MySQL 表与字段（查字典用）
order: 4
---

各微服务 MySQL **表名与字段**字典；按服务名往下翻即可。已落地 DDL 文档与 Server **同名**：见 [数据库脚本](/engineering/tech/sql)（如 `001_init.sql`、`002_p1.sql`）。改已有表：本页 + 对应同名 md + `OnePage-Server/*/db/sql/`。新脚本沿用服务内下一序号，禁止自编 `onepage_xxx.sql`。与 [API 明细](/engineering/process/api-endpoints)、[PRD](/product/spec/prd) 不一致时一并改。

> [!NOTE]
> 推荐 **一个微服务一个库**（如 `onepage_user`）。MVP 也可以先共用一个 MySQL 实例，但表名仍按服务归属理解。

按服务名分节：`auth` → `user` → `content` → …。每张表下列字段名、类型、说明。**Outbox** 各服务相同，只写在文末 [Outbox 表](#outbox-表各服务相同)。

## 通用约定

- 主键：`BIGINT`（雪花或自增，团队选一种坚持用）  
- 时间：`DATETIME(3)`，建议 UTC  
- 删帖/删评：可用 `deleted_at` 软删  
- **不要跨库外键**：别的服务只有 `user_id`、`post_id` 这种 ID

## 库一览

| 库名 | 管什么 |
| --- | --- |
| onepage_auth | 账号、OAuth 绑定 |
| onepage_user | 资料、关注、浏览历史 |
| onepage_content | 笔记、媒体；P1 暂含赞藏评与浏览（DEC-023） |
| onepage_social | 目标库；P1 未单独建库 |
| onepage_message | 私信 |
| onepage_notification | 通知收件箱 |
| onepage_moderation | 举报 |
| onepage_live | 直播房间、场次、间内聊天 |

搜索正文在 **Elasticsearch**，见文末「ES 索引」。

## onepage_auth

### auth_account

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 即全局 `user_id`（注册时生成） |
| phone | VARCHAR(20) UK 可空 | E.164 或国内 11 位 |
| email | VARCHAR(128) UK 可空 | |
| password_hash | VARCHAR(255) 可空 | BCrypt；纯 OAuth 可空 |
| status | TINYINT | 0 正常 1 冻结 |
| created_at | DATETIME(3) | |
| updated_at | DATETIME(3) | |

### auth_oauth_binding

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| user_id | BIGINT | → user 资料 |
| provider | VARCHAR(16) | `github` / `google` |
| provider_user_id | VARCHAR(64) | 第三方唯一 ID |
| created_at | DATETIME(3) | UK(provider, provider_user_id) |

## onepage_user

### user_profile

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| user_id | BIGINT PK | 与 auth_account.id 一致 |
| page_id | VARCHAR(16) UK | **一页号**，对外展示，不可改 |
| nickname | VARCHAR(64) | |
| avatar_object_key | VARCHAR(512) 可空 | MinIO key |
| cover_object_key | VARCHAR(512) 可空 | |
| bio | VARCHAR(500) 可空 | 签名 |
| gender | TINYINT | 0 未知 1 男 2 女（可扩展） |
| created_at | DATETIME(3) | |
| updated_at | DATETIME(3) | |

### user_stat

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| user_id | BIGINT PK | |
| following_count | INT | 关注数 |
| follower_count | INT | 粉丝数 |
| like_received_count | INT | 获赞（笔记被赞） |
| favorite_received_count | INT | 被收藏数 |
| updated_at | DATETIME(3) | 可由 MQ 异步累加 |

### user_follow

P2 已建。脚本 [002_follow.sql](/engineering/tech/sql/onepage-user-service/002_follow)。复合主键，无单独 id。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| follower_id | BIGINT | 关注人；PK 之一 |
| followee_id | BIGINT | 被关注人；PK 之一 |
| created_at | DATETIME(3) | 关注时间 |

### user_unfollow_log

脚本 [003_unfollow_log.sql](/engineering/tech/sql/onepage-user-service/003_unfollow_log)。取关时插入，不改 `user_follow` 删边。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| follower_id | BIGINT | 取关人 |
| followee_id | BIGINT | 被取关人 |
| followed_at | DATETIME(3) | 当时关注边的创建时间 |
| unfollowed_at | DATETIME(3) | 取关时间 |

### browse_history

P1 浏览列表实际读 content 库 `post_view`，本表未建。规划字段如下，拆库时再迁。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| user_id | BIGINT | |
| post_id | BIGINT | |
| viewed_at | DATETIME(3) | 索引 (user_id, viewed_at DESC) |

## onepage_content

### media_asset

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 对外 mediaId |
| owner_id | BIGINT | |
| bucket | VARCHAR(64) | |
| object_key | VARCHAR(512) | 前缀即用途：`avatar/` `cover/` `post_image/` `post_video/` `im_image/` |
| mime_type | VARCHAR(128) | |
| size_bytes | BIGINT | |
| duration_sec | INT 可空 | 视频 |
| status | TINYINT | 0 上传中 1 就绪 2 失败 |
| created_at | DATETIME(3) | |
| blocked_at | DATETIME(3) 可空 | 运营隐藏；社区笔记跳过该媒体 |

### post

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| author_id | BIGINT | |
| title | VARCHAR(200) 可空 | |
| body | TEXT | 正文 |
| post_type | VARCHAR(16) | `text` / `image` / `video` / `mixed` |
| category | VARCHAR(32) | 首页 Tab，对应 `post_category.name` |
| status | VARCHAR(16) | `draft` / `pending` / `published` / `rejected` / `deleted`；Feed 只出 published |
| reject_reason | VARCHAR(200) 可空 | 未通过原因 |
| visibility | TINYINT | 0 公开（发现流）；1 仅自己；2 好友可见（关注流） |
| original | TINYINT | 1 声明原创 |
| content_kind | VARCHAR(16) | 空=不声明；虚构创作 / 个人观点 / 专业内容 |
| event_id | VARCHAR(32) | 关联活动 id，可空串 |
| event_title | VARCHAR(64) | 关联活动标题快照 |
| published_at | DATETIME(3) | 定时发布：未到点不进 Feed |
| created_at | DATETIME(3) | |
| updated_at | DATETIME(3) | |
| deleted_at | DATETIME(3) 可空 | |

### post_category

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| name | VARCHAR(32) UNIQUE | 写入 `post.category` |
| sort_order | INT | 小的在前 |
| enabled | TINYINT | 1 展示 |
| created_at | DATETIME(3) | |

### campaign

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | VARCHAR(32) PK | 写入 `post.event_id` |
| title | VARCHAR(64) | 快照到 `post.event_title` |
| intro | VARCHAR(200) | |
| cover_url | VARCHAR(512) | |
| status | VARCHAR(16) | live / upcoming / ended |
| sort_order | INT | |
| enabled | TINYINT | |
| created_at | DATETIME(3) | |

### topic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| name | VARCHAR(32) UNIQUE | 无 `#` |
| official | TINYINT | 1 官方 |
| sort_order | INT | |
| enabled | TINYINT | |
| created_at | DATETIME(3) | |

### platform_notice

脚本 [009_platform_notice.sql](/engineering/tech/sql/onepage-content-service/009_platform_notice)。创作者首页公告。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| title | VARCHAR(64) | |
| body | VARCHAR(500) | |
| sort_order | INT | |
| enabled | TINYINT | 1 展示 |
| created_at | DATETIME(3) | |

### post_media

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| post_id | BIGINT | |
| media_id | BIGINT | → media_asset |
| sort_order | INT | 多图顺序 |
| role | VARCHAR(16) | `cover` / `gallery` / `video` |

### post_view（P1 暂放本库）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| user_id | BIGINT | PK 之一 |
| post_id | BIGINT | PK 之一 |
| viewed_at | DATETIME(3) | 最近浏览；创作者报表按此落在周期内计一次（用户×笔记，不是每次打开） |

## onepage_social

> P1 下列表实际建在 `onepage_content`，由 content 服务读写。本节目的是字段字典与拆库后对齐。

### post_like

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| user_id | BIGINT | |
| post_id | BIGINT | UK(user_id, post_id) |
| created_at | DATETIME(3) | |

获赞列表：`post_like` JOIN `post`，`post.author_id = 当前用户`，不新建表。

### post_favorite

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| user_id | BIGINT | |
| post_id | BIGINT | UK(user_id, post_id) |
| created_at | DATETIME(3) | |

被收藏列表：`post_favorite` JOIN `post`，`post.author_id = 当前用户`。

### comment

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| post_id | BIGINT | |
| user_id | BIGINT | |
| root_id | BIGINT | 一级评论 id；一级时等于自身 id |
| parent_id | BIGINT 可空 | 直接父评论 |
| content | VARCHAR(2000) | |
| like_count | INT | 冗余计数 |
| status | TINYINT | 0 正常 1 删除 |
| created_at | DATETIME(3) | |

### comment_like

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| user_id | BIGINT | |
| comment_id | BIGINT | UK(user_id, comment_id) |
| created_at | DATETIME(3) | |

### share_log

P1 已建。脚本 [008_creator_metrics.sql](/engineering/tech/sql/onepage-content-service/008_creator_metrics)。复制链接或私信分享各写一行。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| user_id | BIGINT | 分享人 |
| post_id | BIGINT | |
| channel | VARCHAR(16) | `link` / `im` |
| created_at | DATETIME(3) | |

### post_cover_show

Feed 出卡一次一行。脚本同 008。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| viewer_id | BIGINT 可空 | 未登录空 |
| post_id | BIGINT | |
| created_at | DATETIME(3) | |

### post_play_complete

登录用户视频播到约 90%。脚本同 008。同一人同一篇只一行。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| user_id | BIGINT | PK 之一 |
| post_id | BIGINT | PK 之一 |
| created_at | DATETIME(3) | |

## onepage_message

### conversation

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| type | VARCHAR(16) | `single` |
| pair_key | VARCHAR(64) 可空 | 单聊双方 userId 升序拼接，UNIQUE |
| created_at | DATETIME(3) | |
| updated_at | DATETIME(3) | 最后消息时间 |

### conversation_member

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| conversation_id | BIGINT | |
| user_id | BIGINT | UK(conversation_id, user_id) |
| last_read_message_id | BIGINT 可空 | |
| joined_at | DATETIME(3) | |

### message

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| conversation_id | BIGINT | |
| sender_id | BIGINT | |
| msg_type | VARCHAR(16) | `text` / `image` / `post_card` / `live_card` |
| content | JSON | 文本或卡片 payload |
| created_at | DATETIME(3) | 索引 (conversation_id, id) |
| deleted_at | DATETIME(3) 可空 | 运营隐藏；用户列表/未读忽略该行 |

## onepage_notification

### notification

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| user_id | BIGINT | 收件人 |
| type | VARCHAR(32) | 见下表 |
| actor_id | BIGINT 可空 | 触发者 |
| aggregate_key | VARCHAR(128) | 去重键，如 `post:1:like:2` |
| post_id | BIGINT 可空 | |
| comment_id | BIGINT 可空 | |
| payload | JSON 可空 | 扩展 |
| read_at | DATETIME(3) 可空 | |
| created_at | DATETIME(3) | 创建或最近一次合并时间 |

UNIQUE `(user_id, aggregate_key)`：同人同事件只留一行，再投递清 `read_at` 并刷新时间。

**type 枚举（MVP）**

| type | 含义 |
| --- | --- |
| `comment` | 评论我的笔记 |
| `reply` | 回复我的评论 |
| `mention` | @ 我 |
| `like_post` | 赞笔记 |
| `favorite_post` | 藏笔记 |
| `like_comment` | 赞评论 |
| `follow` | 新关注 |
| `review_reject` | 笔记未通过（preview 为运营回复） |

## onepage_moderation

### report

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| reporter_id | BIGINT | |
| target_type | VARCHAR(16) | `post` / `user` |
| target_id | BIGINT | |
| reason_code | VARCHAR(32) | 原因码 |
| detail | VARCHAR(1000) 可空 | |
| status | TINYINT | 0 待处理 1 已处理 |
| handler_id | BIGINT 可空 | 处理人 |
| handle_action | VARCHAR(16) 可空 | dismiss / takedown / restore / ban / unban |
| handle_note | VARCHAR(500) 可空 | |
| handled_at | DATETIME(3) 可空 | |
| created_at | DATETIME(3) | |

## onepage_live

### live_room

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| owner_id | BIGINT | |
| title | VARCHAR(100) | |
| category | VARCHAR(32) | 分类，空为未分；对齐直播 Tab |
| cover_object_key | VARCHAR(512) 可空 | |
| notice | VARCHAR(200) | 本场公告；空则前端用默认欢迎文案 |
| created_at | DATETIME(3) | |

### live_session

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| room_id | BIGINT | |
| stream_key | VARCHAR(64) | 推流 key；MediaMTX 断流钩子按它关场 |
| status | VARCHAR(16) | `idle` / `live` / `ended` |
| started_at | DATETIME(3) 可空 | |
| ended_at | DATETIME(3) 可空 | |
| peak_viewer_count | INT | 心跳在场人数峰值；脚本 [005_peak.sql](/engineering/tech/sql/onepage-live-service/005_peak) |

### live_chat

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| session_id | BIGINT | 场次 |
| user_id | BIGINT | 发言人 |
| body | VARCHAR(200) | 正文；`join` 为「来了」 |
| msg_type | VARCHAR(16) | `text` 发言 / `join` 进房 |
| created_at | DATETIME(3) | |

## Outbox 表（各服务相同）

需要发 MQ 的服务（如 content、social、user）各建一张 `outbox_event`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| aggregate_type | VARCHAR(32) | 如 `post` |
| aggregate_id | VARCHAR(64) | |
| event_type | VARCHAR(64) | |
| payload_json | JSON | |
| status | TINYINT | 0 待发送 1 已发送 2 失败 |
| created_at | DATETIME(3) | |
| sent_at | DATETIME(3) 可空 | |

## Redis 键（非 MySQL）

| 键 | 说明 |
| --- | --- |
| `onepage:ai:settings` | AI 运营配置 JSON（模型、密钥、温度/maxTokens/流式、三段 prompt、RAG 条数/正文截断、耶耶文案、开关）；未写入或 reset 后用环境变量与内置 prompt |
| `live:presence:{sessionId}` | 直播在场 ZSET，member=`u:{userId}` 或 `g:{presenceId}`，score=心跳毫秒；25s 无心跳剔除 |

## Elasticsearch 索引（非 MySQL）

| 索引 | 主要字段 | 说明 |
| --- | --- | --- |
| `onepage-posts` | postId, title, body, author, category, counts, publishedAt | 公开笔记全文 |
| `onepage-users` | userId, pageId, nickname, bio | 用户搜索 |
| `onepage-topics` | topicId, name, official, sortOrder | 启用话题 |
| `onepage-lives` | sessionId, title, category, status, owner, viewerCount | 仅 `status=live` |

## 修订记录

| 日期 | 变更 |
| --- | --- |
| 2026-10-19 | `platform_notice`；`user_unfollow_log` |
| 2026-10-18 | ES `onepage-lives`；索引名与代码对齐（连字符） |
| 2026-10-18 | `live_room.notice` 本场公告 |
| 2026-10-17 | `live_chat.msg_type`；在场 Redis `live:presence` |
| 2026-10-17 | `live_chat` 直播间文字聊天 |
| 2026-10-07 | 全库 SQL 写入 [数据库脚本](/engineering/tech/sql)；P0 已落地 auth/user 资料/media_asset |
| 2026-10-07 | MVP 初稿，与 PRD / 契约对齐 |

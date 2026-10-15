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
| onepage_live | 直播房间与场次 |

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
| object_key | VARCHAR(512) | |
| mime_type | VARCHAR(128) | |
| size_bytes | BIGINT | |
| duration_sec | INT 可空 | 视频 |
| status | TINYINT | 0 上传中 1 就绪 2 失败 |
| created_at | DATETIME(3) | |

### post

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| author_id | BIGINT | |
| title | VARCHAR(200) 可空 | |
| body | TEXT | 正文 |
| post_type | VARCHAR(16) | `text` / `image` / `video` / `mixed` |
| category | VARCHAR(16) | 首页 Tab：旅行/美食/时尚/摄影/生活/运动/游戏/音乐；旧帖可空 |
| original | TINYINT | 1 声明原创 |
| content_kind | VARCHAR(16) | 空=不声明；虚构创作 / 个人观点 / 专业内容 |
| event_id | VARCHAR(32) | 关联活动 id，可空串 |
| event_title | VARCHAR(64) | 关联活动标题快照 |
| status | VARCHAR(16) | `draft` / `published` / `deleted` |
| visibility | TINYINT | 0 公开；1 仅自己（不进发现流） |
| published_at | DATETIME(3) 可空 | |
| created_at | DATETIME(3) | |
| updated_at | DATETIME(3) | |
| deleted_at | DATETIME(3) 可空 | |

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
| viewed_at | DATETIME(3) | 最近浏览 |

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

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| user_id | BIGINT | 分享人 |
| post_id | BIGINT | |
| channel | VARCHAR(16) | `link` / `im` |
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
| handle_action | VARCHAR(16) 可空 | dismiss / takedown / ban |
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
| cover_object_key | VARCHAR(512) 可空 | |
| created_at | DATETIME(3) | |

### live_session

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | |
| room_id | BIGINT | |
| stream_key | VARCHAR(64) | SRS |
| status | VARCHAR(16) | `idle` / `live` / `ended` |
| started_at | DATETIME(3) 可空 | |
| ended_at | DATETIME(3) 可空 | |

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

## Elasticsearch 索引（非 MySQL）

| 索引 | 主要字段 | 说明 |
| --- | --- | --- |
| `onepage_posts` | postId, authorId, title, body, publishedAt | 全文 |
| `onepage_posts_vector` | postId, embedding dense_vector | kNN |
| `onepage_users` | userId, pageId, nickname | 用户搜索 |

## 修订记录

| 日期 | 变更 |
| --- | --- |
| 2026-10-07 | 全库 SQL 写入 [数据库脚本](/engineering/tech/sql)；P0 已落地 auth/user 资料/media_asset |
| 2026-10-07 | MVP 初稿，与 PRD / 契约对齐 |

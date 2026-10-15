---
title: 接口契约
description: 调 API 时共同遵守的规则（不是接口大全）
order: 3
---

所有业务 API **共同遵守**的规则：路径前缀、登录方式、错误格式、分页、上传流程。  
具体路径与 JSON 字段见 [API 接口明细](/engineering/process/api-endpoints)；落库见 [数据库设计](/engineering/tech/database-schema)。

> [!TIP]
> 本仓「保存 Markdown」不算业务 API，见 [工作空间写回约定](/engineering/process/workspace-writeback)。

## 共同规则（背下这几条）

| 项 | 约定 |
| --- | --- |
| 地址前缀 | `/api/v1` |
| 格式 | JSON；生产走 HTTPS |
| 登录 | **Session + Cookie**，名 **`ONEPAGE_SESSION`**；写接口未登录 401。GET Feed/笔记详情/评论列表、`GET /users/{pageId}`、`GET /users/{pageId}/posts`、`GET /users/{pageId}/following` `/followers` 公开，有 Cookie 时仍注入 `X-User-Id`。`GET /posts/mine`、`GET /users/me`、`GET /users/me/suggest`、`GET /users/me/favorites` `/likes` `/history` `/likes-received` `/favorites-received`、`GET/POST /notifications/**`、`GET/POST /conversations/**`、`WS /ws/im`、`POST /posts/{id}/share/im`、`POST/DELETE /users/{pageId}/follow`、`PATCH /posts/{id}`、**`/ops/**`（员工，`OPS_STAFF_USER_IDS`，空则 403）** 必须登录。运营登录须 `audience=ops` |
| 密码 | 注册 8～32 位，须同时包含字母和数字；BCrypt 存储 |
| 登出 | `POST /api/v1/auth/logout` 由**网关** invalidate WebSession，返回 200 |
| 设密 | 已登录 `POST /auth/password`；未登录找回 `POST /auth/password/reset` + `sms scene=reset` |
| 内部调用 | Feign 带 **`X-Internal-Token`**（与 env `INTERNAL_TOKEN` / yml `onepage.internal-token` 相同） |
| 前端 | 请求必须 `credentials: 'include'`；不要把 token 塞 localStorage |
| 服务里谁是我 | 内网用 Gateway 传来的 **`X-User-Id`**，服务自己不解析 Cookie |
| 出错时 | `{ "code", "message", "requestId" }` |
| 列表 | Feed 用 **cursor**；评论可按 `rootId` 分页 |
| 赞/藏 | 防重复：唯一约束或 `Idempotency-Key` |

## 上传文件（三步）

1. `POST /api/v1/uploads/presign` — 拿到临时上传地址  
2. 浏览器 **直传 MinIO**（`PUT`，`Content-Type` 必须与 presign 时的 `mimeType` 一致）。本机 MinIO 请设 `MINIO_API_CORS_ALLOW_ORIGIN`，包含 5173/5174/5175/5176（及 127.0.0.1 对应端口）。这版 PutBucketCors 常不可用。创作者站 :5175 直传缺 5175 会被浏览器拦 CORS。  
3. `POST /api/v1/uploads/complete` — 确认可用，拿到 `mediaId` 再去发帖、绑头像/封面，或发私信图片（`purpose=im_image`）

## 请求分到哪个服务（Gateway 路由）

| 路径里大概是什么 | 服务 |
| --- | --- |
| `/auth/**` | 登录注册 |
| `/users/**` | 用户资料、关注、列表 |
| `/uploads/**`、`/media/**` | 上传与媒体（content，P0） |
| `/posts/**`、`/feeds/**` | 笔记（content，P1） |
| `/comments/**`、`/reactions/**` | 评论、赞藏（P1 合在 content，DEC-023） |
| `/conversations/**`、`/ws/im` | 私信 |
| `/notifications/**` | 通知 |
| `/search/**` | 搜索 |
| `/ai/**` | AI |
| `/live/**` | 直播 |
| `/reports/**` | 用户提交举报（P5，moderation） |
| `/ops/**` | 运营后台（P5；员工 Session；moderation；预览/下架/恢复 Feign content，冻结 Feign auth） |

完整路径列表见 [API 明细](/engineering/process/api-endpoints)。

## 消息队列（RocketMQ）

服务里发生「别人可能关心的事」时，先写 **Outbox**，再发到 MQ。示例：

| 事件 | 谁发 | 谁可能消费 |
| --- | --- | --- |
| 笔记发布 | content | search（建索引） |
| 点赞 | content | notification（本期 Feign，MQ 未消费） |
| 评论 | content | notification（本期 Feign，MQ 未消费） |
| 新关注 | user | notification（本期 Feign，MQ 未消费） |

消息里带 `eventId`（幂等）、`eventType`、`occurredAt`、`payload`。Topic 命名见 [路线图 · RocketMQ](/engineering/tech/mvp-roadmap)。

## CSRF

P0 未接 Spring CSRF。本地 Vite 同源代理 `/api`，Cookie `SameSite=Lax`。上线 HTTPS 后再定自定义头或 CSRF token，并写进本页。

网关路由 `sensitiveHeaders` 必须为空（不要拦 `Set-Cookie`），否则登录种不上 Session。auth 的 Cookie **不要 Base64**（`CookieSerializer.useBase64Encoding=false`），否则网关 WebFlux 用原文去 Redis 对不上，登录会闪一下回到未登录。

## 改契约时的顺序

1. 改 [API 明细](/engineering/process/api-endpoints) 或本页  
2. 改 [数据库设计](/engineering/tech/database-schema) 与 [数据库脚本](/engineering/tech/sql)  
3. 再改 OnePage-Server 的 `db/sql` 与实现，并通知 FE  

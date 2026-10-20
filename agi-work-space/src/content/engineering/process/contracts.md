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
| 登录 | **Session + Cookie**，C 端名 **`ONEPAGE_SESSION`**，运营名 **`ONEPAGE_OPS_SESSION`**（同一 Redis，DEC-046）。Redis 空闲超时 **`SESSION_TIMEOUT`（默认 7d，滑动）**。写接口未登录 401。GET Feed/笔记详情/评论列表、`GET /search`、`GET /live/sessions`、`GET /live/sessions/{id}`、`GET /live/sessions/{id}/messages`、`POST /live/sessions/{id}/heartbeat`、`GET /ai/bootstrap`、`GET /catalog`、`GET /catalog/topics`、`GET /catalog/faq`、`GET /users/{pageId}`、`GET /users/{pageId}/posts`、`GET /users/{pageId}/following` `/followers` 公开，有**对应** Cookie 时仍注入 `X-User-Id`。`GET /posts/mine`、`GET /creator/stats`、`GET /live/stats`、`POST /posts/{id}/play-complete`、`GET /users/me`、`GET /users/me/suggest`、`GET /users/me/favorites` `/likes` `/history` `/likes-received` `/favorites-received`、`DELETE /users/me/history`、`DELETE /users/me/history/{postId}`、`GET/POST /notifications/**`、`GET/POST /conversations/**`、`WS /ws/im`、`POST /posts/{id}/share/im`、`POST/DELETE /users/{pageId}/follow`、`PATCH /posts/{id}`、`POST /ai/posts/polish`、`POST /ai/chat`、`POST /ai/chat/stream`、`POST /live/rooms`、`POST /live/rooms/{roomId}/start` `/end` `/notice`、`GET /live/mine`、`POST /live/sessions/{id}/messages`、`POST /live/sessions/{id}/share/link` `/share/im`、`POST /support`、**`/ops/**`（员工，`OPS_STAFF_USER_IDS`，空则 403）** 必须登录。运营登录须 `audience=ops`；运营站后续请求带头 `X-OnePage-Audience: ops` |
| 密码 | 注册 8～32 位，须同时包含字母和数字；BCrypt 存储 |
| 登出 | `POST /api/v1/auth/logout` 由**网关** invalidate **当前请求选中的** WebSession，返回 200。运营须 `?audience=ops`，只清运营 Cookie |
| 设密 | 已登录 `POST /auth/password`；未登录找回 `POST /auth/password/reset` + `sms scene=reset` |
| OAuth | 登录走 `GET /auth/oauth/{provider}/authorize`；已登录绑定加 `intent=bind`。邮箱已占用不开新号。登录回调 302 首页可带 `oauth=`（`email_taken` / `need_login`）；绑定回调 302 `/settings`（`bind_ok` / `already_bound`） |
| 内部调用 | Feign 带 **`X-Internal-Token`**（与 env `INTERNAL_TOKEN` / yml `onepage.internal-token` 相同） |
| 前端 | 请求必须 `credentials: 'include'`；不要把 token 塞 localStorage |
| 服务里谁是我 | 内网用 Gateway 传来的 **`X-User-Id`**，服务自己不解析 Cookie |
| 出错时 | `{ "code", "message", "requestId" }` |
| 列表 | Feed 用 **cursor**；评论可按 `rootId` 分页 |
| 赞/藏 | 防重复：唯一约束或 `Idempotency-Key` |

## 上传文件（三步）

1. `POST /api/v1/uploads/presign` — 拿到临时上传地址  
2. 浏览器 **直传 MinIO**（`PUT`，`Content-Type` 必须与 presign 时的 `mimeType` 一致）。本机预签名指向 `:9000`，运营 :5176 直传会被 CORS 拦；开发时 `api.putObject` 改走当前源，Vite 把 `/onepage-media` 转到 MinIO（Host 仍是 localhost:9000）。MinIO 仍建议设 `MINIO_API_CORS_ALLOW_ORIGIN`（含 5173–5176）。活动封面只存去掉 query 的公开 URL，不要把预签名整段写入 `cover_url`（512，超长会 500）。  
3. `POST /api/v1/uploads/complete` — 确认可用，拿到 `mediaId` 再去发帖、绑头像/封面，或发私信图片（`purpose=im_image`），或帮助反馈附图（`purpose=feedback`）

## 请求分到哪个服务（Gateway 路由）

| 路径里大概是什么 | 服务 |
| --- | --- |
| `/auth/**` | 登录注册 |
| `/users/**` | 用户资料、关注、列表 |
| `/uploads/**` | 上传（content，P0）。文件读 MinIO 公开 URL，**没有**公网 `GET /api/v1/media/**` |
| `/posts/**`、`/feeds/**`、`/creator/**` | 笔记与创作者报表（content，P1） |
| `/comments/**`、`/reactions/**` | 评论、赞藏（P1 合在 content，DEC-023） |
| `/conversations/**`、`/ws/im` | 私信 |
| `/notifications/**` | 通知 |
| `/search/**` | 搜索（`onepage-search-service`，ES） |
| `/ai/**` | AI（`onepage-ai-service`，HTTP LLM） |
| `/live/**` | 直播 |
| `/reports/**` | 用户提交举报（P5，moderation） |
| `/support/**` | 帮助反馈与入驻/认证申请（moderation） |
| `/catalog/**` | 公开目录（分类/活动/话题/公告/创作字典） |
| `/ops/**` | 运营后台（P5；员工 Session；工单/帮助申请/用户/社区规则/笔记审稿/评论/媒体库/分类活动话题公告字典 CRUD/私信巡查/关注粉丝/收件箱/AI 配置；Feign content+auth+message+user+notification+ai+live） |

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

网关路由 `sensitiveHeaders` 必须为空（不要拦 `Set-Cookie`），否则登录种不上 Session。auth 的 Cookie **不要 Base64**（`CookieSerializer.useBase64Encoding=false`），否则网关 WebFlux 用原文去 Redis 对不上，登录会闪一下回到未登录。auth / 网关启动类不要写 `@EnableRedisHttpSession` / `@EnableRedisWebSession`，超时只走 yml `spring.session.timeout`，否则 Redis TTL 会被钉成 30 分钟（DEC-045）。Session Redis 只认 JSON（`springSessionDefaultRedisSerializer`），auth 与网关必须同一实现（DEC-047）。

## 改契约时的顺序

1. 改 [API 明细](/engineering/process/api-endpoints) 或本页  
2. 改 [数据库设计](/engineering/tech/database-schema) 与 [数据库脚本](/engineering/tech/sql)  
3. 再改 OnePage-Server 的 `db/sql` 与实现，并通知 FE  

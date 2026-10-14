---
title: 架构图与时序图
description: 看图理解系统与关键流程
order: 3
---

用图说明系统边界、微服务拆分和关键流程。下文部分图含规划中的 ES / RocketMQ / SRS，**未建的不要当成已上线**。文字细节见 [技术架构](/engineering/tech/architecture)、[路线图](/engineering/tech/mvp-roadmap)。  
本工作空间 `npm run dev` 预览下 Mermaid 会自动渲染。建议顺序：**系统全景 → 微服务 → 登录 → 发帖**。  
登录/注册/短信开户/设密/找回的完整架构、流程、时序见 **[登录注册流程](/engineering/tech/auth-flows)**（P0 实况）。

## 1. 系统上下文（谁连着谁）

```mermaid
flowchart LR
  subgraph users [Users]
    Browser[PC_Browser]
  end
  subgraph onepage [ONEPAGE_Platform]
    Web[OnePage_web]
    GW[onepage_gateway]
    Svc[Microservices]
  end
  subgraph infra [Infrastructure]
    MySQL[(MySQL)]
    Redis[(Redis)]
    MinIO[(MinIO)]
    ES[(Elasticsearch)]
    RMQ[RocketMQ]
    SRS[SRS_HLS]
  end
  subgraph external [External]
    SMS[SMS_Provider]
    OAuth[GitHub_Google]
    LLM[LLM_API]
  end
  Browser --> Web
  Web --> GW
  GW --> Svc
  Svc --> MySQL
  Svc --> Redis
  Svc --> MinIO
  Svc --> ES
  Svc --> RMQ
  Svc --> SRS
  Svc --> SMS
  Svc --> OAuth
  Svc --> LLM
```

## 2. 后端逻辑架构

```mermaid
flowchart TB
  subgraph edge [Edge]
    GW[onepage_gateway]
  end
  subgraph platform [Platform_Services]
    Auth[onepage_auth_service]
  end
  subgraph core [Core_Domain]
    User[onepage_user_service]
    Content[onepage_content_service]
    Social[onepage_social_service]
  end
  subgraph engagement [Engagement]
    Msg[onepage_message_service]
    Notify[onepage_notification_service]
  end
  subgraph intelligence [Intelligence]
    Search[onepage_search_service]
    AI[onepage_ai_service]
  end
  subgraph other [Other]
    Live[onepage_live_service]
    Mod[onepage_moderation_service]
  end
  Browser[OnePage_web] --> GW
  GW --> Auth
  GW --> User
  GW --> Content
  GW --> Social
  GW --> Msg
  GW --> Notify
  GW --> Search
  GW --> AI
  GW --> Live
  GW --> Mod
  Content -->|Feign| User
  Social -->|Feign| Content
  AI -->|Feign| Search
  Content -->|Outbox| RMQ[RocketMQ]
  Social -->|Outbox| RMQ
  User -->|Outbox| RMQ
  RMQ --> Search
  RMQ --> Notify
```

## 3. 前端微应用架构

```mermaid
flowchart TB
  subgraph shell [apps_shell]
    Layout[Layout_Nav]
    AuthUI[Login_Session]
    Bell[Notify_IM_Badges]
    SearchEntry[Global_Search]
  end
  subgraph sites [Separate_Sites]
    Creator[app_creator]
  end
  subgraph remotes [Module_Federation_Remotes]
    Feed[app_feed]
    UserApp[app_user]
    IM[app_im]
    LiveApp[app_live]
  end
  subgraph packages [packages]
    UI[ui_shadcn]
    API[api_client]
    Types[types]
  end
  shell -->|full_page_jump| Creator
  shell --> Feed
  shell --> UserApp
  shell --> IM
  shell --> LiveApp
  Creator --> API
  Feed --> UI
  Feed --> API
  UserApp --> UI
  UserApp --> API
  IM --> API
  LiveApp --> API
  API --> GW[Gateway_api_v1]
```

| 子应用 | 路由 | 主要 API |
| --- | --- | --- |
| shell | `/`, `/login`, `/settings` | `/api/v1/auth/**` |
| app-creator | 独立站 `/`（本机 :5175；壳 `/creator` 只跳转） | 创作者数据（P0 mock；后续 content） |
| app-feed | `/explore`, `/post/:id`, `/create` | posts, comments, reactions |
| app-user | `/u/:pageId` | users, follows |
| app-im | `/messages` | conversations, `WS /ws/im` |
| app-live | `/live` | live, `WS /ws/live` |

## 4. 时序：登录与 Session（Cookie）

概要。短信开户、注册默认昵称、登出、设密/找回见 [登录注册流程](/engineering/tech/auth-flows)。

```mermaid
sequenceDiagram
  participant B as Browser
  participant W as OnePage_web
  participant G as Gateway
  participant A as auth_service
  participant U as user_service
  participant R as Redis

  B->>W: AuthModal 勾选协议后提交
  W->>G: POST /auth/login 或 /sms/login 或 /register
  Note over G: 上述路径公开，不验 Session
  G->>A: 转发
  A->>A: 校验账密或验证码；短信未注册则建号（无密码）
  A->>U: Feign 建档或取摘要
  A->>R: 写入 Spring Session
  A->>B: Set-Cookie ONEPAGE_SESSION HttpOnly
  B->>G: GET /api/v1/users/me Cookie
  G->>R: 读取 WebSession
  G->>U: X-User-Id
  U->>B: 200 用户资料
```

## 5. 时序：发帖 + Outbox + 搜索索引

```mermaid
sequenceDiagram
  participant B as Browser
  participant G as Gateway
  participant C as content_service
  participant DB as MySQL
  participant Job as outbox_relay
  participant MQ as RocketMQ
  participant S as search_service
  participant ES as Elasticsearch

  B->>G: POST /api/v1/posts Cookie
  G->>C: X-User-Id
  C->>DB: BEGIN insert post + outbox
  C->>DB: COMMIT
  C->>B: 201 postId
  Job->>DB: poll outbox pending
  Job->>MQ: PostPublished
  Job->>DB: outbox sent
  MQ->>S: consume
  S->>ES: upsert index
```

## 6. 时序：媒体上传（预签名直传 MinIO）

```mermaid
sequenceDiagram
  participant B as Browser
  participant G as Gateway
  participant C as content_service
  participant M as MinIO

  B->>G: POST /api/v1/uploads/presign
  G->>C: 鉴权后签发
  C->>B: presignedUrl objectKey
  B->>M: PUT 文件直传
  M->>B: 200
  B->>G: POST /api/v1/uploads/complete
  G->>C: 确认对象存在
  C->>B: mediaId 可绑定帖子
```

## 7. 时序：点赞 → 通知（异步）

```mermaid
sequenceDiagram
  participant B as Browser
  participant G as Gateway
  participant Soc as social_service
  participant DB as MySQL
  participant MQ as RocketMQ
  participant N as notification_service
  participant R as Redis

  B->>G: POST /api/v1/reactions/like
  G->>Soc: X-User-Id
  Soc->>DB: insert like + outbox Liked
  Soc->>B: 200
  MQ->>N: consume Liked
  N->>DB: insert notification
  N->>R: incr unread badge
  Note over B: 壳层铃铛拉取或 WS 推送
```

## 8. 时序：AI 助手问答（RAG）

```mermaid
sequenceDiagram
  participant B as Browser
  participant G as Gateway
  participant AI as ai_service
  participant Sch as search_service
  participant ES as Elasticsearch
  participant LLM as LLM_Provider

  B->>G: POST /api/v1/ai/chat
  G->>AI: question
  AI->>Sch: retrieve context
  Sch->>ES: kNN + keyword
  ES->>Sch: snippets
  Sch->>AI: passages
  AI->>LLM: prompt + context
  LLM->>AI: answer
  AI->>B: 200 带引用片段
```

## 9. 时序：私信 HTTP（开会话 / 拉历史 / 发文本）

```mermaid
sequenceDiagram
  participant B as Browser
  participant G as Gateway
  participant M as message_service
  participant U as user_service
  participant DB as MySQL
  participant R as Redis

  B->>G: POST /conversations Cookie
  G->>M: X-User-Id
  M->>U: Feign briefs
  M->>DB: conversation + members
  M->>B: 201 conversationId
  B->>G: GET messages / POST text
  G->>M: persist / mark read
  M->>R: 频道 onepage:im:push
  Note over B: 在线由 WS 收；断线约 3 秒轮询
```

## 9c. 时序：分享笔记到私信

```mermaid
sequenceDiagram
  participant B as Browser
  participant G as Gateway
  participant C as content_service
  participant M as message_service
  participant DB as MySQL

  B->>G: POST /posts/{id}/share/im Cookie
  G->>C: X-User-Id
  C->>C: 笔记对分享者可见
  C->>M: Feign share-card + token + X-User-Id
  M->>DB: 开会话（若无）+ message post_card
  M->>C: messageId
  C->>B: 201 messageId
```

## 9b. 时序：私信 WebSocket

```mermaid
sequenceDiagram
  participant B as Browser
  participant G as Gateway
  participant M as message_service
  participant R as Redis
  participant DB as MySQL

  B->>G: WS /ws/im Cookie
  G->>G: Session 写入 X-User-Id
  G->>M: lb:ws 升级
  M->>B: op connected
  B->>G: POST /conversations/{id}/messages
  G->>M: persist
  M->>DB: message
  M->>R: onepage:im:push
  R->>M: 本机订阅
  M->>B: op message 发送者与对端
```

## 10. 时序：基础直播（开播 / HLS 观看）

```mermaid
sequenceDiagram
  participant Host as Host_Browser
  participant View as Viewer_Browser
  participant G as Gateway
  participant L as live_service
  participant SRS as SRS

  Host->>G: POST /api/v1/live/rooms/start
  G->>L: create session
  L->>SRS: 分配 streamKey
  L->>Host: pushUrl playUrl_HLS
  Host->>SRS: RTMP push
  View->>G: GET live list
  View->>SRS: HLS play
```

## 11. 数据存储归属（简图）

```mermaid
flowchart LR
  subgraph mysql [MySQL_per_service_DB]
    D1[onepage_auth]
    D2[onepage_user]
    D3[onepage_content]
    D4[onepage_social]
    D5[onepage_message]
    D6[onepage_notification]
  end
  Redis[(Redis_Session_Cache)]
  MinIO[(MinIO_Media)]
  ES[(ES_Search_Vector)]
  RMQ[(RocketMQ_Events)]
```

## 图例与约定

- 实线箭头：同步 HTTP / Feign / 用户请求。
- Outbox → RocketMQ：异步最终一致，见路线图「Outbox」一节。
- 生产环境：浏览器只访问 Gateway（及静态资源）；业务服务不对外暴露端口。

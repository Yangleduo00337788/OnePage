---
title: 技术架构
description: 前后端用什么、怎么连在一起
order: 1
---

前后端技术栈、仓库目录规划，以及请求与数据在系统里怎么流动。看图用 [架构图与时序图](/engineering/tech/diagrams)。比 [路线图](/engineering/tech/mvp-roadmap) 更细；改选型时**先改路线图和本页**，再改代码。

## 先分清两个「前端」

| | agi-work-space（本仓） | OnePage-web（产品） |
| --- | --- | --- |
| 给谁 | 团队看文档、改 MD | 真实用户逛社区 |
| 技术 | Vue + Vite 文档壳 | Vue 微前端 + 业务 API |

下面「前端技术栈」指 **OnePage-web**，除非特别说明。

## 前端（OnePage-web）

- **Vue 3.5 + TypeScript + Vite 6**，包管理用 **pnpm** 单仓多应用。  
- 界面：**shadcn-vue + Tailwind 4**，组件集中在 `packages/ui`。  
- **站点拆分**：社区 `apps/shell`（本机 5174）与创作者 `apps/app-creator`（本机 5175）是两个前端，入口整页跳转。规划中的 feed / user / im / live 仍可后续用微前端嵌进壳。  
- 调 API：**带 Cookie**（`credentials: 'include'`），类型由 OpenAPI 生成。  
- 私信、直播：**WebSocket**，握手时带同一套 Session。

规划目录：

```text
apps/shell、app-creator、app-feed、app-user、app-im、app-live
packages/ui、api-client、types
```

路由与子应用对应见 [前端结构](/engineering/frontend)。

## 后端（OnePage-Server）

- **Java 21**，**Spring Boot 3.4.5** + **Spring Cloud 2024.0.1** + **Spring Cloud Alibaba 2023.0.3.2（Nacos 发现）**。  
- **Gateway** 对外（8080）；服务之间马上要结果用 **Feign + lb**；可以晚一点的用 **RocketMQ**（P1 只写 Outbox，暂不消费）。  
- **MyBatis-Plus** 访问 **MySQL**；DDL 手工脚本，**不用 Flyway**。完整 SQL 在工作空间 [数据库脚本](/engineering/tech/sql)，P0 执行入口在各服务 `db/sql/001_init.sql`。  
- 登录：**Spring Session + Redis**，Cookie **`ONEPAGE_SESSION`**（见 [接口契约](/engineering/process/contracts)）。  
- 文件 **MinIO** 预签名直传；搜索 **ES**、消息 **仅 RocketMQ**（尚未接到 P0）。

本地开发：中间件在 **WSL Docker**（`~/docker/compose.yml`：Redis / Nacos / MinIO；MySQL 仍本机）。IDEA 启动四个主类（user 8082 → auth 8081 → content 8083 → gateway 8080）。**yml 只写 `${变量}`**，值来自**进程环境变量**（与上线一致）。本地由 IDEA 把 `OnePage-Server/.env` 注入进运行配置，程序不读该文件。键以 `env.example` 为准，与四服务 yml 对齐。MinIO 浏览器直传靠 compose 里 `MINIO_API_CORS_ALLOW_ORIGIN`（这版 PutBucketCors 常 NotImplemented）。

已落地目录（仅四服务；message / notification / search / ai / moderation / live 未建模块）：

```text
onepage-parent、onepage-common、onepage-gateway
onepage-auth-service、onepage-user-service、onepage-content-service
```

user 目前只有 `me` / 改资料 / 按一页号查资料。帖子列表、赞藏历史、关注接口未实现。content 已提供发帖、Feed、赞藏评、分享链接。

Java 包分层：`web` HTTP、`service` 编排、`client` Feign、`mapper`/`entity` 表、`config` 本服务 Bean。`common` 放统一响应、错误码、`X-User-Id`。类和方法上有中文注释，说明注解用途和「为什么不在业务服务解析 Cookie」等约定；接口字段仍以 [契约](/engineering/process/contracts) 为准。

## 版本锁定（BOM）

实施时在 `onepage-parent` 里锁版本；每季度可升 patch，不用 SNAPSHOT。

应用 BOM（`onepage-parent`）：

| 组件 | 版本 |
| --- | --- |
| Java（业务进程） | 21 |
| Spring Boot / Cloud / Alibaba | 3.4.5 / 2024.0.1 / 2023.0.3.2 |
| nacos-client | **3.2.4**（与本机 Server 对齐，覆盖 SCA BOM 里的 2.4.x） |
| Vue / Vite / pnpm | 3.5.x / 6.x / 9.x |
| MinIO Java SDK | 8.6.0（桶 CORS API；8.5.14 没有 `SetBucketCorsArgs`） |
| Maven | `maven.compiler.parameters=true`（parent pom）。`@PathVariable` 必须写名字，否则 Boot 3 登录会 500 |

本机中间件（IDEA 开发机，2026-10-07 登记）：

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Nacos | **3.2.4 Standalone（Java 17）** | 控制台默认账号 `nacos`/`nacos`；命名空间 `public`。四个服务 `server-addr` 必须写 **`"[::1]:8848"`**，不要写 `127.0.0.1`：本机 IPv4 `:8848` 常被 Windows svchost 占用，HTTP 只在 IPv6 `::1`；旁路 `8849` 不是 Discovery 地址 |
| Redis | **8.0** | Session + 验证码；P0 已用 |
| MinIO | **RELEASE.2025-02-07T23-21-09Z** | 桶 `onepage-media`。content 启动写公开读；PutBucketCors 在这版常报 NotImplemented，不挡启动。浏览器直传靠 MinIO 环境变量 `MINIO_API_CORS_ALLOW_ORIGIN`（须含 5173 文档、5174 社区、5175 创作者） |
| Elasticsearch | **9.5.4** | P3 搜索再用；不要用 8.x 客户端硬连 |
| RocketMQ | **5.5.0** | P1 只写 Outbox，暂不消费 |

## 数据怎么走（四条线）

1. **浏览 / 读**：浏览器 → 产品前端 → Gateway → 服务 → MySQL 或 ES。  
2. **发帖**：content 写 MySQL + Outbox（P1 不发 MQ）。  
3. **点赞等**：P1 由 content 写同一库；通知消费留 P2。  
4. **传图/视频**：先要上传凭证 → 浏览器直传 MinIO → 再绑定到笔记。

图示：[架构图与时序图](/engineering/tech/diagrams)。

## 本仓文档壳（agi-work-space）

只负责渲染 `src/content` 里的 Markdown、配置中心写回。约定见 [工作空间写回约定](/engineering/process/workspace-writeback)。

## 延伸阅读

| 主题 | 文档 |
| --- | --- |
| 表与字段 / DDL | [数据库设计](/engineering/tech/database-schema) · [数据库脚本](/engineering/tech/sql) |
| HTTP 接口 | [契约](/engineering/process/contracts) · [API 明细](/engineering/process/api-endpoints) |
| 登录注册图 | [登录注册流程](/engineering/tech/auth-flows) |
| 产品功能 | [PRD](/product/spec/prd) |

---
title: 架构与 MVP 路线图
description: 技术总览：三个仓库、怎么拆服务、先做什么
order: 2
---

ONEPAGE 的技术总览：三个仓库怎么分工、服务怎么拆、MVP 分几步交付。改范围先改本页再动代码。 **P1 主链路已通**（Gateway / auth / user / content；壳已接 Feed 与详情）。

> [!NOTE]
> 看图更直观：[架构图与时序图](/engineering/tech/diagrams)。  
> 栈的明细表：[技术架构](/engineering/tech/architecture)。  
> 功能细节：[产品需求 PRD](/product/spec/prd)。

## 三个仓库各干什么

| 仓库 | 一句话 |
| --- | --- |
| **agi-work-space**（你现在打开的） | 项目文档 + 本文工作空间 |
| **OnePage-web** | 产品前端（社区 + 创作者；P5 再加运营后台） |
| **OnePage-Server** | 后端微服务 |

三个文件夹在同一父目录下（例如 `OnePage/`）。**业务功能不要写进 agi-work-space 的 Vue 里**。

## 产品长什么样（技术视角）

ONEPAGE = 笔记流（图/文/视频）+ 关注与互动 + **通知和私信分开** + AI + 基础直播。  
PC 浏览器访问；登录用 **Cookie + 服务端 Session**（不用 JWT 做 MVP）。

## 技术怎么搭（记住这几条就够）

1. **入口**：所有 API 走 Spring Cloud Gateway（`:8080`）。下游用 Nacos **`lb://服务名`**，服务自己注册，控制台不用手工点注册。  
2. **登录态**：Session 放在 Redis；Cookie 名 **`ONEPAGE_SESSION`**。网关验 Cookie，内网只认 `X-User-Id`；服务间内部接口再带 `X-Internal-Token`。  
3. **数据**：业务 **MySQL**（一服务一库）；文件 **MinIO**；搜索 **ES**（P3）；缓存和 Session **Redis**。  
4. **异步**：只用 **RocketMQ**（P1 起）；发帖等用 **Outbox**。P0 还没有 MQ。  
5. **前端**：pnpm 仓。已有 **shell**（社区 :5174）、**app-creator**（创作者 :5175）、**app-ops**（运营 :5176，治理接口未接）。feed / 用户 / 私信 / 直播子应用尚未拆。

版本号（Boot 3.4、Vue 3.5 等）见 [技术架构 · 版本锁定](/engineering/tech/architecture#版本锁定bom)。

## Outbox 用一句话说清

用户发帖时，**帖子和「待发送事件」在同一笔 MySQL 事务里提交**；后台任务再把事件推到 RocketMQ，搜索、通知等慢慢跟上来。需要立刻知道结果的（比如用户是否存在）才用 Feign **同步**调别的服务。

## 后端有哪些服务

| 服务 | 主要负责 |
| --- | --- |
| gateway | 统一入口、验 Session |
| auth | 登录、注册、OAuth |
| user | 资料、一页号、关注、个人页列表 |
| content | 笔记、媒体 |
| social | 赞、藏、评论、分享 |
| message | 私信 |
| notification | 评论/@/赞藏/关注等通知 |
| search | 搜索、索引 |
| ai | 润色、问答 |
| moderation | 举报单与运营处理（8086；**不要**再拆 admin 服务，见 DEC-034） |
| live | 直播房间与播放地址 |

早期可以把 content 和 social **部署在一起**，但代码包建议分开，以后好拆。

## 前端有哪些子应用

| 子应用 | 给谁 | 本机 |
| --- | --- | --- |
| shell | C 端社区（顶栏、登录、Feed、通知铃铛） | 5174 |
| app-creator | C 端创作者中心（发帖、笔记、报表） | 5175 |
| app-ops | 内部运营后台（工单/用户/私信/笔记/评论/媒体库/分类活动话题；P5） | 5176 |
| app-feed | 刷流、发帖、看笔记（规划，现仍在 shell） | — |
| app-user | 个人主页、粉丝/关注列表（规划） | — |
| app-im | 私信（规划，现仍在 shell） | — |
| app-live | 直播（规划） | — |

运营后台与创作者中心不是一类站：作者管自己的内容，员工管全站治理。命名与边界见 [DEC-034](/records/decisions)。

## MVP 做与不做

**要做**：登录（短信/账密/GitHub/Google）、发帖、Feed、赞藏评、关注、主页与列表、通知+私信分开、分享、举报、AI 润色与问答、直播推流+HLS 观看。

**先不做**：礼物、连麦、带货、复杂推荐、CMS 式运营后台（推荐位、复杂权限矩阵等）。P5 治理台要完整（预览 + 下架/恢复 + 封禁/解冻）。

## 分几步交付

| 阶段 | 团队能演示什么 |
| --- | --- |
| P0 | 能登录、能传图、有用户资料、能设密/找回密码 |
| P1 | 能发帖、能刷流、能赞藏评 |
| P2 | 能关注、能收通知、能私信 |
| P3 | 能搜索、能用 AI 助手 |
| P4 | 能开播、能看直播 |
| P5 | 能举报；员工能在 `app-ops` 站内预览并下架/恢复/封禁/解冻；能巡查私信并隐藏；能浏览媒体库；能看关注/粉丝并拆边、看收件箱并删通知 |

**当前**：P1 主链路已通。P2 关注已落在 user-service。P2 通知已落 `onepage-notification-service`（8084）。P2 私信已落 `onepage-message-service`（8085）：单聊文本/图片 HTTP、`WS /ws/im`、分享笔记卡片 `POST /posts/{id}/share/im`。获赞/被收藏列表已接。尚未实现：AI、搜索、直播。评论 @ 已接（一页号或昵称，通知 `mention`）。Outbox 只落库。本地 IDEA 七进程（含 moderation 8086）；yml 只引用环境变量。短信 `SMS_MOCK` 验证码 `888888`。content 需 `002_p1.sql`、`003_category.sql`、`004_post_settings.sql`、`005_ops_catalog.sql`，已有库另执行 `006_ops_media_block.sql`；user 关注需 `002_follow.sql`；通知需 `onepage-notification-service/db/sql/001_init.sql`；私信需 `onepage-message-service/db/sql/001_init.sql`，已有库另执行 `002_ops_hide.sql`（并在 `.env` 加 `MESSAGE_PORT`、`MYSQL_MESSAGE_DB`）。message-service 需 Redis 与 `MEDIA_PUBLIC_BASE`。治理需 `onepage-moderation-service/db/sql/001_init.sql`（`.env` 加 `MODERATION_PORT`、`MYSQL_MODERATION_DB`；`OPS_STAFF_USER_IDS` 必填，空则拒绝）。

## 往下翻

- [技术架构](/engineering/tech/architecture) · [登录注册流程](/engineering/tech/auth-flows)  
- [接口契约](/engineering/process/contracts) · [API 明细](/engineering/process/api-endpoints) · [数据库设计](/engineering/tech/database-schema) · [数据库脚本](/engineering/tech/sql)  
- [领域设计](/engineering/tech/domain)

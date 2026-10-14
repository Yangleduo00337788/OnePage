---
title: 项目决策
description: 决策、原因、备选与影响
order: 1
---

POM 维护。每条决策单独一节，新的写在上面。聊天结论当天搬到这里。

## DEC-033 评论 @ 先联想再选定，不要只靠事后解析

- **日期：** 2026-10-14
- **状态：** 已采纳
- **背景：** 只靠发评后扫 `@token`，用户不知道有没有人、点了谁。
- **决策：** 输入 `@` 后调 `GET /users/me/suggest?q=`（必须登录，避免被 `GET /users/{pageId}` 当成用户号）。列表浮在输入框上方。空词给已关注；有词匹配一页号前缀或昵称包含。点选写入 `@昵称`，并把一页号放进 `mentionPageIds`。通知仍按 DEC-032 精确解析。
- **替代：** 等 P3 ES 搜索（否决：评论 @ 不能等）；不做面板只解析（否决：不好用）。
- **影响：** user-service 增加 `/suggest`；详情评论框键盘上下/回车可选中。

## DEC-032 评论 @ 解析一页号或昵称，通知类型 `mention`

- **日期：** 2026-10-14
- **状态：** 已采纳
- **背景：** 通知 Tab「评论和@」已预留 `mention`，此前评论不解析 @。
- **决策：** 发评 body 可带 `mentionPageIds`；服务端同时用正则 `@([^\s@]{1,32})` 扫正文。content Feign `GET /internal/users/by-keys` 按一页号或精确昵称解析。命中则投递 `mention`（幂等键 `mention:{commentId}:{recipient}`）。已是本条评论/回复通知对象或自己的不再发。找不到的 token 忽略，评论仍成功。最多 10 人。公开主页 `GET /users/{pageId}` 在一页号未命中且昵称全站唯一时也可打开。
- **替代：** 只认一页号（否决：用户习惯 @ 昵称）；模糊匹配（否决：误伤）。
- **影响：** user-service 增加 by-keys；详情评论 @ 可点；通知「评论和@」出「提到了你」。

## DEC-031 私信图片走 MinIO `im_image`，消息里只存 mediaId+url

- **日期：** 2026-10-14
- **状态：** 已采纳
- **背景：** DDL 已预留 `msg_type=image`。公网若允许任意 URL 可钓鱼。
- **决策：** 先 `POST /uploads/presign`（`purpose=im_image`，jpeg/png/webp/gif，≤10MB）再 complete，然后 `POST /conversations/{id}/messages` 只传 `mediaId`。message-service Feign content 核对本人、已就绪、objectKey 前缀 `im_image/`，再写入公开 `url`。列表预览 `[图片]`。
- **替代：** 复用 `post_image`（否决：和发帖媒体混路径）；浏览器直接传 URL（否决：不可信）。
- **影响：** content 增加 purpose；message-service 要 `MEDIA_PUBLIC_BASE`。

## DEC-030 私信实时用 WebSocket，轮询只作降级

- **日期：** 2026-10-14
- **状态：** 已采纳
- **背景：** DEC-027 先用 HTTP 轮询把单聊发出去。PRD 实时仍要求 WebSocket。
- **决策：** 浏览器连同源 `WS /ws/im`（Cookie Session）。网关验 Session、注入 `X-User-Id`，`lb:ws://onepage-message-service`。落库仍走 REST；提交后 Redis 频道 `onepage:im:push` 推到发送者与对端在线连接。消息页在线不轮询，断线约 3 秒 REST。图片见 DEC-031。
- **替代：** 继续只轮询（否决：延迟差）；STOMP/SockJS（否决：契约是裸 `/ws/im`）。
- **影响：** message-service 增加 Redis 与 websocket 依赖。网关路由 `im-ws` 的 `response-timeout` 为 -1，避免 10 秒掐断长连接。

## DEC-029 品牌锁用设计稿开本书 + 透明底

- **日期：** 2026-10-14
- **状态：** 已采纳
- **背景：** 产品壳试过斜体字标、胶囊、纸飞机和生成图，侧栏易裁切或和灰底打架。用户提供锁套与 favicon 定稿。
- **决策：** 全站（社区、创作者、文档壳）用同一套 PNG：锁套为开本书 + `ONE` 墨 `PAGE` `#ff2442`，透明底；favicon 为透明底红书。社区左栏 **190px**，锁套高 **30px**（`BrandLogo`）。名称 ONEPAGE（一页）与 slogan「记录生活每一页」/ Every Day, A New Page 不变。不用中文 Logo。
- **替代：** CSS 自绘字标（否决：和设计稿不一致）；白底/黑底 PNG（否决：侧栏、登录海报、地址栏会出色块）。
- **影响：** 资源在三站 `public/logo-*.png` 与 `favicon*.png`；深色主题社区切 `logo-dark.png`。

## DEC-028 获赞列表给互动人；笔记卡片只走分享接口

- **日期：** 2026-10-14
- **状态：** 已采纳
- **背景：** PRD 获赞/被收藏是「谁赞了/藏了你」；契约曾写 `CursorPage<PostBrief>`。私信公网若允许任意 `post_card` 会伪造卡片。
- **决策：** 列表为 `ReceivedItem`（actor + postId/title/时间），仅 `GET /users/me/likes-received` `|favorites-received`。`post_card` 只由 `POST /posts/{id}/share/im` 写入，公网发消息仍只 `text`。
- **替代：** 列表出帖卡片（否决：对不上 PRD 头像+一页号）；浏览器直接 POST `post_card`（否决：可伪造）。
- **影响：** 他人主页「获赞与收藏」只展示计数，点开提示仅作者可见。

## DEC-027 私信第一刀用 HTTP 轮询，不上 WebSocket

- **日期：** 2026-10-13
- **状态：** 已被 DEC-030 取代（轮询仅断线降级）
- **背景：** P2 要能单聊。WebSocket 要网关升级、在线路由和 Redis，会拖住「先能发到」。
- **决策：** 先做 `onepage-message-service` 的 REST（开会话、列表、拉消息、发文本、未读合计）。社区 `/messages` 约 3 秒轮询。单聊用 `pair_key`（两 userId 升序）UNIQUE。当时公网只 `msgType=text`；图片见 DEC-031，笔记卡片见 DEC-028。
- **替代：** 同期上 WS（否决：联调面太大）；会话放 content 库（否决：与通知一样要独立进程和库）。
- **影响：** 契约里 `WS /ws/im` 仍保留为未实现。图片消息另做。

## DEC-026 不做独立「整体架构」HTML

- **日期：** 2026-10-11
- **状态：** 已采纳
- **背景：** 文档壳顶栏曾新开标签打开 `system-map.html`。多轮改画法后仍不满意，决定去掉该功能。
- **决策：** 删除顶栏入口、Vite 对 `/system-map.html` 的托管、以及 `engineering/tech/system-map.html` / `system-map.md`。架构事实仍写在 [技术架构](/engineering/tech/architecture)、[路线图](/engineering/tech/mvp-roadmap)、[架构图与时序图](/engineering/tech/diagrams)（Mermaid）。
- **替代：** 继续维护独立 HTML 总图（否决：成本高、难定稿）。
- **影响：** Agent 回写表不再要求改 system-map.html。

## DEC-025 工作空间是项目长期事实源

- **日期：** 2026-10-11
- **状态：** 已采纳
- **背景：** 用 Agent 长期开发 ONEPAGE，若干年后应只读工作空间（或让 Agent 读工作空间）就能掌握架构、功能与约定，而不是翻聊天记录。
- **决策：** `agi-work-space/src/content` 为知识空间。改产品代码或架构/功能约定时，同一轮回写对应 MD（PRD、架构/路线图、契约、库表、视觉、决策、状态）。聊天不是事实源。密钥不进文档。
- **替代：** 只靠代码与 Git 历史还原（否决：Agent 成本高、易漏产品意图）；只靠对话记录（否决：不可检索、会丢）。
- **影响：** 回写表写在 `OnePage/AGENTS.md` 与 harness `AGENTS.md`。漏写回即视为任务未完成。

## DEC-024 Java 规约放在 Harness，Agent 必读

- **日期：** 2026-10-11
- **状态：** 已采纳
- **背景：** 希望 Agent 改后端时遵守阿里 Java 手册。官方手册全文受版权保护，不能贴进仓库。
- **决策：** 在 `space/harness/java-alibaba.md` 写本项目 Java 约定（章节与手册对齐）。改 `OnePage-Server` 前必须读。可携带约束写进 Git 跟踪的 `OnePage/AGENTS.md` 与 harness `AGENTS.md`（含回写表）。**不**用 `.cursor/rules`（该目录 gitignore）。IDE 可用 P3C 插件做检查。
- **替代：** 整本手册 Markdown 进仓（否决：版权）；只口头说「按阿里规范」（否决：Agent 读不到）；把规则放 `.mdc`（否决：换机丢失）。
- **影响：** 与契约/表结构冲突时仍以契约和 DDL 为准。换电脑只要 clone/pull 即可读到约束。

## DEC-023 P1 赞藏评合进 content 库

- **日期：** 2026-10-10
- **状态：** 已采纳
- **背景：** 字典里赞藏评属于 `onepage_social`。再开第五个进程和库会拖垮本机 IDEA 联调。
- **决策：** P1 把 `post` / `post_media` / `outbox_event` / `post_like` / `post_favorite` / `comment` / `comment_like` / `post_view` 建在 `onepage_content`，由 content 服务提供 `/posts` `/feeds` `/reactions` `/comments`。关注流与发现流暂同一套「最新已发布」。Outbox 只落库，不接 RocketMQ 消费。GET 发现流/笔记/评论公开，已登录则网关注入 `X-User-Id` 以填 likedByMe。
- **替代：** 立刻拆 social-service（否决：多一个端口/库/运行配置）。
- **影响：** 独立社交库尚无 Server 脚本，不要预编文件名。P2 关注/通知若独立服务，按该服务 `db/sql/001_init.sql` 再建文档。

## DEC-022 本地与上线都靠进程环境变量

- **日期：** 2026-10-10
- **状态：** 已采纳
- **背景：** 密码曾写在 yml。需要一份本地清单，但启动方式必须和上线一样：环境变量 → yml `${VAR}`，不能在 Java 里读 `.env`。
- **决策：** yml 只写占位符。上线由 K8s/主机注入环境变量。本地一份 `OnePage-Server/.env`（模板 `env.example`），由 **IDEA 运行配置 Environment files** 注入成进程环境变量后再启动四个主类。Nacos 仍 `[::1]:8848`。
- **替代：** 程序启动时自己解析 `.env`（已去掉）；机密写进 yml（DEC-010 已废止）。
- **影响：** 四个主类配置都要挂同一份 `.env`。不要把 `.env` 提交或打进镜像。

## DEC-021 社区侧栏「发布」只进创作者站

- **日期：** 2026-10-10
- **状态：** 已采纳
- **背景：** `/create` 是社区占位页，真正发帖在创作者中心。
- **决策：** 侧栏「发布」走 `/create`，灰底选中。主区复用首页瀑布流，不放空白/占位页。点击同时 `window.open` 创作者站。`/creator`（更多）新开后仍回首页。
- **影响：** 社区不再做发布工作台。

## DEC-020 作者主页 P0 先挂 shell `/u/:pageId`

- **日期：** 2026-10-10
- **状态：** 已采纳
- **背景：** 详情作者卡要点进个人主页；`app-user` 尚未建。
- **决策：** 社区壳增加 `/u/:pageId`（`UserPage`）。一页号优先用搜索占位用户，否则由作者名哈希。详情作者卡内头像、昵称、关注、三项数字、帖图都进该页并关掉弹窗。自己的资料仍是 `/me`。
- **影响：** 关系列表、真实用户 API 仍待 P1 `app-user`。

## DEC-019 创作者站只登录、用独立页

- **日期：** 2026-10-09
- **状态：** 已采纳
- **背景：** 创作者中心与社区同一账号，后台是数据展示，不必在此开户。登录做成弹窗会叠在后台壳上，也不符合「进站先登录」。
- **决策：** 未登录时 :5175 整页独立登录（验证码 / 密码，勾协议）。不提供注册、不弹窗。视觉为生活拼贴背景（项目自绘图，不使用外部海报原图）+ 右侧 400px、圆角 20px 白表单。无账号去社区注册。登录成功再进后台；退出回到登录页。Session 仍走 `api-client` Cookie。
- **影响：** 社区 `AuthModal` 仍负责注册/找回；创作者站不再跳 5174 登录。

## DEC-018 创作者中心是独立站点，整页跳转

- **日期：** 2026-10-09
- **状态：** 已采纳
- **背景：** 创作者工作台和逛社区不是一类事。文档工作空间占用 5173。产品要「离开社区、进入另一个网站」，不要壳内换页，也不用 Module Federation。
- **决策：** 本机端口：5173 文档工作空间、**5174 社区壳**、**5175 创作者中心**。侧栏「更多 → 创作者中心」`window.open` 新开标签到 `VITE_CREATOR_ORIGIN`（默认 `http://localhost:5175`），不覆盖社区页。壳 `/creator` 同样新开标签并回到首页。创作者站自带顶栏「返回社区」。发布 / 主播入驻跳回社区站对应路径。两边各自把 `/api` 代理到网关。
- **影响：** 创作者站是后台：左栏管理导航，顶栏头像退出。不再把创作者页嵌进壳。上线后应对两个域名做登录打通（本机 localhost 不同端口 Cookie 通常仍能带上）。

## DEC-017 耶耶页先打磨前端，模型与 RAG 暂缓

- **日期：** 2026-10-08
- **状态：** 已采纳
- **背景：** 产品要先还原耶耶助手首页与对话体验；后端生成暂不做。
- **决策：** `/yehyeh` 用本地会话（localStorage）+ mock 回复。壳上不显示顶搜。交互按点点布局与视觉规范：249px 历史栏、825×150 白底输入卡、预设可直接发、能力卡、对话页用户灰气泡 / AI 无气泡。搜索 `?q=` 仍直接开聊。
- **影响：** 接入模型时替换 `YehYehPage` 的 mock 与 `stores/yehyeh.ts` 持久化；顶搜隐藏只作用于该路由。

## DEC-016 短信开户后可设密；未登录走短信找回

- **日期：** 2026-10-08
- **状态：** 已采纳
- **背景：** 未注册手机号用验证码登录会建号且无密码，退出后不能账密登录。PRD 找回密码原标 P0.5。
- **决策：** 已登录 `GET /auth/account` + `POST /auth/password`（无密直接设，有密要旧密码）。未登录 `sms/send scene=reset` + `POST /auth/password/reset`，成功即登录。网关公开路径收紧，设密要 Session。
- **影响：** 须重启 Gateway + Auth。流程写入 [登录注册流程](/engineering/tech/auth-flows)。

## DEC-015 登出在网关清 Session；注册密码须字母+数字

- **日期：** 2026-10-08
- **状态：** 已采纳
- **背景：** 退出登录请求挂住，刷新才像退出——auth invalidate 与网关 WebSession 抢同一份 Redis。注册只校验「至少 6 位」，简单密码能过。
- **决策：** `POST /logout` 由网关 `session.invalidate()` 后返回 200，不再转发给 auth。密码 8～32 位且须同时含字母和数字，前后端都拦。
- **影响：** 改完需重启 Gateway（以及 Auth 以启用密码规则）。

## DEC-014 侧栏「更多」为弹窗，不设独立 `/more` 页

- **日期：** 2026-10-08
- **状态：** 已采纳
- **背景：** 底栏「更多」原先进占位页；需要隐私/协议、帮助反馈、创作者与合作、快捷键、桌面快捷方式、主题、退出登录。
- **决策：** 点击「更多」打开侧栏旁弹窗，无右侧遮罩。菜单项在弹窗内三级页（返回上一级），不切独立页。主题同一行用显示器/太阳/月亮滑块。直达路由 `/privacy` 等仍保留。添加到桌面下载 `ONEPAGE.url`。主题写入 `localStorage` `onepage-theme`。
- **影响：** 不再使用 `/more` 占位路由。

## DEC-013 本机 Nacos HTTP 连 `[::1]:8848`

- **日期：** 2026-10-08（踩坑在 10-07，文档补记）
- **状态：** 已采纳
- **背景：** `127.0.0.1:8848` 被 Windows svchost 占用；Nacos 3.2.4 HTTP 只映在 IPv6 `::1`。写 `8849` 对不上 Discovery。
- **决策：** 四服务 Nacos 地址一律 `[::1]:8848`（现由 `.env` 的 `NACOS_SERVER_ADDR` 注入 yml）。
- **影响：** 换机器若 IPv4 8848 空闲，仍建议保持 `[::1]` 与现网一致，避免再踩。

## DEC-012 PC 首页按原型构图，像素以走查为准

- **日期：** 2026-10-08
- **状态：** 已采纳
- **背景：** 先按小红书截图搭过一版，后以 `onepage-pc-home.jpg` 定分区；左栏/顶栏/卡片又按实机改了多轮。
- **决策：** 静态原型只定分区。颜色、导航、图标、选中胶囊、卡片悬停以 [视觉规范](/design/system/specs) 为事实源。不再追原型里的兴趣分类、铃铛头像、黑底发布钮。
- **影响：** 改 PC 壳先改视觉规范再改 `OnePage-web/apps/shell`。

## DEC-011 本机中间件版本按开发机锁定

- **日期：** 2026-10-07
- **状态：** 已采纳
- **决策：** Redis 8.0、ES 9.5.4、RocketMQ 5.5.0、MinIO `RELEASE.2025-02-07T23-21-09Z`、Nacos 3.2.4 Standalone（进程 Java 17）。业务服务仍是 Java 21。`nacos-client` 升到 **3.2.4**，不要用 SCA 自带的 2.4.x 去连 3.x Server。
- **影响：** Nacos 3 默认命名空间是 `public`。ES / RocketMQ 未进 P0。

## DEC-010 本地开发不用 Docker、配置先写 yml

- **日期：** 2026-10-07
- **状态：** 已采纳
- **背景：** 本机已有 MySQL / Redis / Nacos / MinIO；Docker 不是日常开发方式。环境变量清单对 IDEA 不友好。
- **决策：** IDEA 只起四个 Java 进程。依赖本机安装。~~密码写在 yml。~~ **已被 [DEC-022](#dec-022-本地配置用-envyml-只写占位符) 替代：yml 占位符 + `.env`。**
- **备选：** 全程 Docker Compose。
- **影响：** 见 DEC-022。

## DEC-009 服务发现用 Nacos，网关 lb:// 服务名

- **日期：** 2026-10-07
- **状态：** 已采纳
- **背景：** 静态 `localhost:808x` 路由无法模拟真实网关。
- **决策：** Spring Cloud Alibaba Nacos Discovery；`@EnableDiscoveryClient` 自动注册，控制台不用手工注册。Feign / Gateway 只用服务名。
- **备选：** Eureka；网关写死 URI。
- **影响：** 本机必须先开 Nacos。四个服务同一 `group`。

## DEC-008 登录 Session+Cookie，不用 JWT

- **日期：** 2026-10-07
- **状态：** 已采纳
- **背景：** PC Web MVP，需要服务端可吊销、与网关统一鉴权。
- **决策：** Spring Session Redis；Cookie `ONEPAGE_SESSION`；网关写入 `X-User-Id`。
- **备选：** JWT。
- **影响：** 网关必须转发 `Set-Cookie`。

## DEC-007 库用 MySQL，脚本手工执行，不用 Flyway / Postgres

- **日期：** 2026-10-07
- **状态：** 已采纳
- **决策：** 一服务一库；DDL 在工作空间 `engineering/tech/sql/` 与各服务 `db/sql/`；MyBatis-Plus；主键应用侧雪花。
- **备选：** Postgres；Flyway 自动迁。
- **影响：** 改表必须三处同步（字典 MD、工作空间 SQL、Server SQL）。

## DEC-006 异步只用 RocketMQ + Outbox

- **日期：** 2026-10-07
- **状态：** 已采纳
- **决策：** 不做 Kafka。业务与「待发事件」同事务写 Outbox，再投递。P0 尚未接入 MQ。
- **备选：** Kafka；同步 Feign 打通知。

## DEC-005 P0 短信可 mock

- **日期：** 2026-10-07
- **状态：** 已采纳
- **决策：** `onepage.sms.mock=true` 时验证码固定 `888888`，不调运营商。

## DEC-001 内容用 Markdown 工作空间，而不是每篇一个 Vue 页

- **日期：** 2026-09-22
- **状态：** 已采纳
- **背景：** 文档变多时，独立页面会拖垮维护和性能。
- **决策：** 项目知识全部放在 `src/content/` 的 Markdown 中；Vue 只提供导航、渲染、搜索、主题、角色与本地写回。
- **备选：** 每篇一个 `.vue`；或用 CMS 存文档（已否决，保持纯前端 + Git 里的 Markdown）。
- **影响：** 新增上下文 = 新增或修改 `.md`。不要为业务文档加路由组件。

## DEC-002 写权限按配置中心的七个角色划分

- **日期：** 2026-09-22
- **状态：** 已采纳
- **背景：** 全员可改会导致职责不清；完全锁死又没法在页面里维护。
- **决策：** RD / FE / PM / QA / OP / UIUE / POM 七角色，可写范围见 [项目角色](/project/governance/roles)。
- **备选：** 单一管理员；或按 Git 分支权限。
- **影响：** 改目录归属时必须同时改 `src/lib/roles.ts` 与角色表。

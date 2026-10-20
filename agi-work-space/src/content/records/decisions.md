---
title: 项目决策
description: 决策、原因、备选与影响
order: 1
---

POM 维护。每条决策单独一节，新的写在上面。聊天结论当天搬到这里。

## DEC-068 机审按风险分与可配阈值分流

- **日期：** 2026-10-20
- **状态：** 已采纳
- **背景：** 二元 pass/flag 不好调松紧。运营要按风险程度打分，低分自动过、高分才给人。
- **决策：** 模型输出 `score` 0–100（越高越险）。运营「审稿」页配置 `reviewRiskLimit`（默认 40）。`score < 阈值` 自动 `published`；`score >= 阈值` 或解析失败/超时 → 人审，**不自动驳回**。分数写入 `post.ai_review_score`。试审接口一并回分数。
- **替代：** 继续布尔 pass（否决：调阈值要改 prompt）；高分自动驳回（否决：误杀）。
- **影响：** 执行 content `013_post_ai_review_score.sql`。重启 ai、content、moderation。运营改阈值后保存再生效。

## DEC-067 审稿模型与对话 AI 分套配置

- **日期：** 2026-10-20
- **状态：** 已采纳
- **背景：** 机审一度复用耶耶/润色的地址和密钥。运营要单独配审稿 API、密钥，并能单独探测。
- **决策：** Redis `onepage:ai:settings` 增加 `reviewLlmBaseUrl` `reviewLlmApiKey` `reviewLlmModel`。运营 AI 左侧第四项「审稿」：开关、地址、模型名、密钥、探测、试审、审稿 prompt。不回落到对话模型。未配审稿密钥则公开帖进人审。可选环境变量 `LLM_REVIEW_BASE_URL` `LLM_REVIEW_API_KEY` `LLM_REVIEW_MODEL`（默认可空）。探测 `POST /ops/ai/review/probe`；试审 `POST /ops/ai/review/try`（不发帖）。
- **替代：** 审稿缺省沿用对话密钥（否决：两套场景不该绑死）。
- **影响：** 重启 ai、moderation。运营刷新后在「审稿」页填独立密钥。

## DEC-066 公开笔记文字机审，未过才进人审

- **日期：** 2026-10-20
- **状态：** 已采纳
- **背景：** 公开图文/视频/长文全部进运营待审，队列会堵。违禁词已 400 拦死。用户要求 AI 先审，未过再给人。
- **决策：** 公开帖仍先 `pending`，发帖接口立刻返回。content 事务提交后异步 Feign `POST /internal/ai/review`（只送标题正文）。`pass` → 自动 `published`（等同运营通过，写出盒）。`flag` / 解析失败 / 超时 / 未开开关 / 无密钥 → 留待审，写 `ai_review`+`ai_review_reason`，**不自动驳回**。仅自己/好友可见仍直接发布。图/视频不扫画面（下一步 OCR）。运营「发帖」页可关开关、改审稿 prompt。
- **替代：** 机审失败自动驳回（否决：误杀）；发帖同步等模型（否决：拖垮发布）。
- **影响：** 执行 content `012_post_ai_review.sql`。重启 content、ai、moderation。运营待审列表带机审结论。

## DEC-065 运营违禁词覆盖发帖评论私信弹幕

- **日期：** 2026-10-20
- **状态：** 已采纳
- **背景：** DEC-062/063 词表只拦改资料。发帖、评论、私信、弹幕、帮助申请仍可带同样的词。
- **决策：** 同一张 `community_policy` 词表（`BannedText`，忽略大小写去空白含子串）。拦：注册/改昵称、签名、笔记标题正文（含草稿）、评论、私信文本、直播标题/本场公告/聊天、帮助申请名称/联系方式/正文、运营新建话题。命中 **400**「含有不允许的内容」。注册填了违禁昵称不再静默改成「一页用户」。图/视频/头像/封面/私信图**不扫画面**（无 OCR），只继续拦体积。
- **替代：** 每条链路各写一份词表（否决：对不齐）；上 OCR（否决：超出当前治理范围）。
- **影响：** 重启 user、content、message、live、moderation、**auth**（注册违禁昵称须把 user 的 400 转成注册 400，不能 500）。运营「社区规则」空词表则这些入口都不拦。

## DEC-064 运营工作台按治理台待办优先

- **日期：** 2026-10-20
- **状态：** 已采纳
- **背景：** 工作台只有库存数字和分类/活动/话题柱图，不像业界治理后台：先进待办、看今日增量、从首页点进队列。
- **决策：** `GET /ops/overview` 补 `usersToday` `postsPending` `postsRejected` `postsToday` `commentsToday` `reportsToday` `supportPending`。页面改问候+待办合计、三张待办卡、KPI 带今日、快捷入口、待办构成/笔记状态/今日新增图、最新待审/工单/申请。目录条数仍返回，首页不再当主指标。不接直播在线数（直播不进菜单）。
- **替代：** 只改前端排版继续用旧 9 个数（否决：待审/帮助申请看不见）。
- **影响：** 重启 user、content、moderation。工作台并行拉 `GET /ops/posts?taken=pending`、`GET /ops/reports?status=pending`、`GET /ops/support?status=pending`。

## DEC-063 违禁词与媒体上限运营可配

- **日期：** 2026-10-20
- **状态：** 已采纳
- **背景：** DEC-062 把词表和 10MB/200MB 写死在 user/content。运营要能改。
- **决策：** 表 `community_policy`（moderation，单行 id=1）。员工 `GET/POST /ops/policy`。user/content Feign `GET /internal/policy`，内存缓存 15 秒；治理挂了用上次或默认。词表不做舆情审核。视频时长仍不做。
- **替代：** 跟 AI 一样塞 Redis（否决：规则要落库、重启不丢）；每个服务各写一份（否决：对不齐）。
- **影响：** 执行 moderation `003_community_policy.sql`；重启 moderation、user、content。运营左栏治理「社区规则」。

## DEC-062 同帖赞藏合并、资料违禁词、画面弹幕接聊天

- **日期：** 2026-10-20
- **状态：** 已采纳
- **背景：** PRD「待 PM 细化」里三项已到该落地；直播底栏「弹幕」一直提示未开放。DEC-054 当时否决 overlay。
- **决策：** `like_post`/`favorite_post`/`like_comment` 的 `aggregate_key` 按帖/评论不按人；payload 记 `actorIds`/`actorCount`，列表显示「等 N 人」。昵称 1～24、签名 0～160，词表拦广告引流。图片（含头像/封面/笔记图/私信/反馈）10MB，视频 200MB。画面弹幕复用右侧 `live_chat` 文本（不含进房「来了」），底栏可关。礼物/连麦仍不做。
- **替代：** 每人一条赞通知（否决：刷屏）；弹幕独立 WS（否决：已有轮询聊天）。
- **影响：** 重启 notification、content、user；社区通知页与运营用户收件箱读 `actorCount`。旧键带 userId 的历史行不自动合并。

## DEC-061 OAuth 不按邮箱静默并号；冲突后登录再绑定

- **日期：** 2026-10-18
- **状态：** 已采纳
- **背景：** 手机号注册并填写与 GitHub/Google 相同的邮箱后，再点第三方会新开 B 号（绑定只认平台用户 id）。业界可按已验证邮箱自动并号，但一页注册邮箱未验证，静默合并有接管风险。
- **决策：** 未绑定且对方邮箱已被占用 → 不开新号，302 `?oauth=email_taken`，提示用手机/密码登录原号。已登录在设置页 `intent=bind` 绑定 GitHub/Google（不要求邮箱相同）。对方 id 已绑别人则 `already_bound`。将来有邮箱验证后再考虑自动并号。
- **替代：** 静默把 OAuth 登进邮箱已有账号（否决：邮箱未验证）；继续裂成两个号（否决：笔记不共用）。
- **影响：** 重启 auth；社区壳读 `oauth` 查询参数；`GET /auth/account` 增加 `githubBound` `googleBound`。

## DEC-060 服务模块、根包、启动类同一段名字

- **日期：** 2026-10-18
- **状态：** 已采纳
- **背景：** IDEA Services 里 `NotifyApplication` / `ImApplication` 和 Maven 模块 `onepage-notification-service` / `onepage-message-service` 对不上；自动探测的 `*Application` 配置和模块名配置叠成两份。
- **决策：** `onepage-{name}-service` → 根包 `com.onepage.{name}` → 启动类 `{Name}Application`。通知从 `com.onepage.notify.NotifyApplication` 改为 `com.onepage.notification.NotificationApplication`；私信从 `com.onepage.im.ImApplication` 改为 `com.onepage.message.MessageApplication`。业务类（`NotifyController`、`ImService` 等）不改名。IDEA 运行配置只保留模块名，不保留按启动类自动生成的那一份。
- **替代：** 只改 IDEA 显示名、包仍用 `notify`/`im`（否决：启动类仍对不上模块）。
- **影响：** 重启 notification、message；IDEA 刷新 Maven 后灰名单 `*Application` 会消失。

## DEC-059 在播、顶栏联想、耶耶检索进 Elasticsearch

- **日期：** 2026-10-18
- **状态：** 已采纳
- **背景：** 社区搜索直播 Tab 仍 MySQL `LIKE` 标题；顶栏「正在直播 / 热门话题」写死暂无；耶耶 RAG 只 `type=post`，话题命中不进上下文。
- **决策：** 索引 `onepage-lives`（字段对齐 `LiveItem`：`sessionId` `title` `category` `status` `owner` 等）。只收 `status=live`；关播删除。live 开/关播后 Feign `POST/DELETE /internal/index/lives`；search 回填 `GET /internal/live/search` 并清掉已结束场次。`GET /search?type=live|all` 出 `lives[]`。顶栏有关键词走 `type=all`；空关键词直播仍 `GET /live/sessions`、话题仍目录（避免空词倒库）。耶耶 `type=all`，笔记或话题任一命中即生成。私信/通知/直播聊天/草稿/运营 CRUD 不进 ES。
- **替代：** 直播继续 live 列表 LIKE（否决：和笔记/用户/话题分裂）；空词 match_all 填顶栏（否决：会把全库倒给联想）。
- **影响：** 重启 search、live、ai、gateway；壳直播 Tab 与顶栏联想改 `GET /search`。

## DEC-058 搜索话题进 Elasticsearch

- **日期：** 2026-10-18
- **状态：** 已采纳
- **背景：** `GET /search?type=topic` 一直空；社区话题 Tab 用 MySQL `GET /catalog/topics` LIKE，和笔记/用户不在同一套检索。
- **决策：** 索引 `onepage-topics`（`topicId` `name` `official` `sortOrder`）。只收 `enabled` 话题。content 保存/停用/发帖抽 hashtag 后 Feign `POST/DELETE /internal/index/topics`；search 启动与定时回填 `GET /internal/search/topics`。社区 `GET /search?type=topic` 出 `topics[]`。发帖联想仍走目录。直播仍走 live 列表。
- **替代：** 继续目录 LIKE（否决：看不见全文检索、和笔记/用户分裂）。
- **影响：** 重启 search + content；壳话题 Tab 改 `GET /search`。

## DEC-057 OBS 断流后宽限期自动关播

- **日期：** 2026-10-18
- **状态：** 已采纳
- **背景：** 房主只点「结束直播」才关场；OBS 停推后列表仍显示直播中，观众要等手动关播。产品改为断流后自动关播。
- **决策：** live-service 每 2s 查推流是否还在：优先 `LIVE_MTX_API_BASE`（MediaMTX `/v3/paths/get` 的 `ready`），否则 GET HLS `index.m3u8`。见过推流后再断，才写入 Redis ZSET `live:unpublish`，`LIVE_UNPUBLISH_GRACE_SECONDS`（默认 15）后关场；期内再推取消。未推过流不关。MediaMTX `runOnNotReady` 钩子可选（更快），不是必配。观众仍靠 `peek` / 心跳看到 `ended`。
- **替代：** 只靠手贴 MediaMTX curl 钩子（否决：和业界「接入层探测、业务轮询/订阅」不一致，本机漏配就不关）；立刻关播（否决：OBS 闪断会误关）。
- **影响：** `.env` 加 `LIVE_UNPUBLISH_GRACE_SECONDS`，可选 `LIVE_MTX_API_BASE`；重启 live-service。创作者开播台约 5s 拉 `GET /live/mine`。

## DEC-056 分享这场直播走 `live_card`；本场公告落房间；直播中不可改资料

- **日期：** 2026-10-18
- **状态：** 已采纳
- **背景：** 底栏分享只复制当前 URL；右栏公告是写死欢迎文案。PRD REQ-D2 要求一页号不可改，离开直播去改资料也不允许。
- **决策：** `POST /live/sessions/{id}/share/link` 与 `/share/im`（live Feign message 写 `live_card`）。`live_room.notice` 房主 `POST /rooms/{id}/notice` 可改；空则前端默认欢迎文案。`PATCH /users/me` 不接受 `pageId`；正在开播则 409。已有库执行 `004_notice.sql`。
- **替代：** 分享只复制前端 `location.href`（否决：和笔记分享不一致、无卡片）；公告写死（否决：本场无法改）。
- **影响：** 重启 live / message / user / gateway；社区直播间分享弹层；创作者开播台公告；「我的」直播中改资料提示先关播。

## DEC-055 直播在场用 Redis ZSET 心跳，进房通知落 `live_chat.join`

- **日期：** 2026-10-17
- **状态：** 已采纳
- **背景：** `GET session` 每次 +1 只增不减；打开详情把人数刷高。要真实在房人数和「xxx 来了」。
- **决策：** `POST /live/sessions/{id}/heartbeat` 公开（登录则 `X-User-Id`）。ZSET `live:presence:{sessionId}`，25s 无心跳剔除。登录观众首次进房写 `msg_type=join`，右栏灰字「昵称 来了」；房主和游客不计这条。人数 = ZCARD。不新开 WS。
- **替代：** 继续计数器 +1（否决：离开不减）；进房走站内通知 inbox（否决：不是直播间场景）。
- **影响：** 已有库执行 `003_presence.sql`；重启 live-service 与 gateway。

## DEC-054 直播间聊天走 live-service HTTP，不进 IM、不做画面弹幕

- **日期：** 2026-10-17
- **状态：** 已采纳
- **背景：** 直播间右栏一直是占位。DEC-052 不做礼物/连麦/画面弹幕。私信是另一套会话。
- **决策：** 表 `live_chat`。`GET /live/sessions/{id}/messages` 公开（`after` 增量，最多 50）。`POST` 须登录，1～200 字；关播 409。社区约 3s 轮询。主播昵称红色。画面底栏「弹幕」仍提示未开放。
- **替代：** 复用 message-service / WS（否决：边界不同）；画面弹幕 overlay（否决：DEC-052）。
- **影响：** 已有 `onepage_live` 须执行 `002_chat.sql`；重启 live-service；网关公开 GET 列表/详情/聊天。

## DEC-053 开播只在创作者中心，社区直播页只看

- **日期：** 2026-10-17
- **状态：** 已采纳
- **背景：** DEC-052 把创作者「去开播」指到社区 `/live`，和「发帖进创作者、社区只消费」不一致。
- **决策：** 开播/OBS 密钥/关播确认落 `app-creator`（左栏「直播」、首页「去开播」）。社区 `/live` 只列表与进房；空态可跳创作者站。房主在直播间仍可确认关播。「进入直播间」新开社区 `/live/:sessionId`。
- **替代：** 社区右下继续开播（否决：和创作者工作台重复）。
- **影响：** 改 `CreatorLive.vue`；社区去掉开播 FAB。API 不变。

## DEC-052 P4 直播走独立 `onepage-live-service`，不内嵌 SRS

- **日期：** 2026-10-16
- **状态：** 已采纳
- **背景：** P3 搜索/耶耶已接；`/live` 仍空态。MVP 只要开播、列表、HLS 观看。
- **决策：** 新建 `onepage-live-service`（8089，库 `onepage_live`）。房间+场次落 MySQL；同一主播同时一场 `live`。推流/HLS 用环境变量前缀拼 `stream_key`，不在本仓跑 SRS。在线人数 Redis 近似。社区 `/live` 列表与点开观看；创作者「去开播」仍进社区直播页。搜索直播 Tab 调 `GET /live/sessions`。礼物/连麦/弹幕不做。
- **替代：** 把直播塞进 content（否决：边界不同）；本期上完整 SRS 集群（否决：环境未备）。
- **影响：** `.env` 加 `LIVE_PORT`、`MYSQL_LIVE_DB`、`LIVE_PUSH_BASE`、`LIVE_HLS_BASE`。须执行 `001_init.sql`，重启 gateway + 新起 LiveApplication。本机可用 MediaMTX（HLS `:8888`）；`hlsVariant` 用 `mpegts`。`playUrlHls` 为 `{LIVE_HLS_BASE}/{key}/index.m3u8`；社区弹层用 hls.js（Chrome 不能直接给 `<video src>`）。默认 `lowLatency` 在 VLC 常无画面。

## DEC-051 运营可配 AI；耶耶默认文案不编辑即发送

- **日期：** 2026-10-16
- **状态：** 已采纳
- **背景：** 模型密钥只在 `.env`；首页三条联想不应一进来就占高度。DEC-038 曾不把 AI 放进运营菜单。
- **决策：** 运营左侧「AI」分三项：模型、耶耶、发帖。仍读写 `GET/POST /ops/ai`（moderation Feign ai-service），保存只带本页字段。配置落 Redis `onepage:ai:settings`，环境变量当默认。密钥 GET 只回掩码。可改三段 system prompt。耶耶 RAG：先 `GET /search?type=post` 再把笔记注入 user，可配检索条数和正文截断；无命中不调模型。模型页可配温度、`max_tokens`、流式；温度与 token 写入 Chat Completions，流式只作用于 `POST /ai/chat/stream`（社区耶耶走 SSE；关闭流式则一次生成后一条 delta）。润色仍整段 JSON。可清除密钥、`POST /ops/ai/reset` 回到环境变量、`POST /ops/ai/probe` 探测连通。公开 `GET /ai/bootstrap` 只出耶耶预设与联想。首页默认灰字与折叠联想；未点输入框点「问耶耶」发出默认文案；点输入框后清空默认文案并展开联想。问答主文只归纳，用 `[n]` 对来源文字胶囊（点开笔记弹层），不把检索正文贴进回答。
- **替代：** 单独 MySQL 库（否决：配置量小）；密钥继续只认 `.env`（否决：运营无法改）。
- **影响：** 须重启 ai、moderation、gateway。见 [API 明细](/engineering/process/api-endpoints)。

## DEC-050 P3 耶耶与发帖润色走 HTTP LLM，服务名 `onepage-ai-service`

- **日期：** 2026-10-16
- **状态：** 已采纳
- **背景：** 搜索已接 ES；耶耶仍 mock，发帖无润色。用户确认先不引入 LangChain4j / Spring AI，服务名须品牌前缀。
- **决策：** 新建 `onepage-ai-service`（8088）。`POST /ai/posts/polish`、`POST /ai/chat` 须登录。用 RestClient 调兼容 OpenAI 的 `/chat/completions`。问答先 Feign `GET /search?type=post` 再生成；无命中不编造。密钥空则润色 503、问答回「暂时不能回答」（不再先查 ES）。会话仍 localStorage。创作者编辑页「AI 优化」「续写」只改文案，不自动发布。耶耶先落用户气泡，回复未到时显示「耶耶正在想…」。
- **替代：** Spring AI / LangChain4j（否决：本期只要 HTTP）；把 AI 塞进 search-service（否决：检索与生成边界分开）。
- **影响：** `.env` 加 `AI_PORT`、`LLM_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL`。须重启 gateway + 新起 AiApplication。见 [API 明细](/engineering/process/api-endpoints)。

## DEC-049 P3 全局搜索上 Elasticsearch

- **日期：** 2026-10-16
- **状态：** 已采纳
- **背景：** P2/P5 可演示后下一步是搜索；耶耶 RAG 也要同一套检索。
- **决策：** 新建 `onepage-search-service`（8087）。索引公开已发布笔记与用户到 ES 9.5.4。`GET /api/v1/search` 公开。MQ 未消费，发帖/改资料用 Feign 投递，启动与每 10 分钟回填。Java API Client 8.17.4 走 REST。话题/直播/耶耶本期不做。
- **替代：** MySQL `LIKE`（否决：撑不住后续 RAG）；等 RocketMQ 再索引（否决：本机还没消费）。
- **影响：** `.env` 加 `SEARCH_PORT`、`ES_URIS`、`ES_USERNAME`、`ES_PASSWORD`（开了 xpack.security 才填账密）。须重启 gateway + content + user + search。见 [API 明细](/engineering/process/api-endpoints)。

## DEC-048 社区互动补齐：评论树、草稿、好友可见、定时、历史删除

- **日期：** 2026-10-16
- **状态：** 已采纳
- **背景：** 运营 P5 可用后，社区详情评论是扁平列表；创作者「暂存」只离开不落库；好友可见/定时在编辑页被拦截；「我的」历史不能删；通知一进列表就全标已读。
- **决策：** 详情展示 2 层回复并可赞、可举报评论（`targetType=comment`）。发帖 `draft` 写入 `status=draft`；`visibility=friend`（2）进关注流不进发现流；`scheduledAt` 写入 `published_at`，Feed 只出已到点。浏览历史 `DELETE` 单条/清空。通知点开条目再标已读。私信列表预览图片/笔记卡片。创作者待处理始终展示草稿与审核中数量。
- **替代：** 好友可见另建关注图缓存表（否决：先用 followee-ids）；定时用独立列与扫表任务（否决：先复用 `published_at`）。
- **影响：** 无新 SQL。须重启 content / user / message / moderation。见 [API 明细](/engineering/process/api-endpoints)。

## DEC-047 Session 写入 Redis 用 JSON，不用 JDK 序列化

- **日期：** 2026-10-16
- **状态：** 已采纳
- **背景：** Redis 里 `spring:session:sessions:*` 的 Hash 值是 JDK 二进制，工具里看起来像乱码。
- **决策：** auth 与网关都提供同名 Bean `springSessionDefaultRedisSerializer`，实现为 `GenericJackson2JsonRedisSerializer`。**读写都只认 JSON**。两边必须同一实现。本机已重新登录后不再兼容 JDK 二进制。
- **替代：** 继续 JDK（否决：无法人读）；读写 JSON、读取再兼容 JDK（过渡期已过，去掉）。
- **影响：** 重启 auth+网关。Redis 里残留的 JDK Hash 可手动删，或等 TTL；带着旧 Cookie 访问按未登录处理。见 [登录注册流程](/engineering/tech/auth-flows)。

## DEC-046 C 端与运营两张 Cookie，一份 Redis Session

- **日期：** 2026-10-16
- **状态：** 已采纳
- **背景：** Cookie 按 host 不按端口。本机社区 :5174、创作者 :5175、运营 :5176 共用 `ONEPAGE_SESSION`。员工在运营站登录会覆盖 Redis 里同一份 Session，社区账号被挤下线。
- **决策：** 仍一套账号库、一份 Redis。C 端（社区+创作者）Cookie **`ONEPAGE_SESSION`**；运营 Cookie **`ONEPAGE_OPS_SESSION`**。选哪一张：路径 `/api/v1/ops/**`，或 query `audience=ops`，或头 `X-OnePage-Audience: ops`。运营登录只读写运营 Cookie，不碰 C 端。运营站 `setApiAudience('ops')`，登出 `POST /auth/logout?audience=ops`。
- **替代：** 两套账号库（否决：DEC-034）；Cookie 带端口（浏览器不支持）；只靠 Origin（否决：代理会改 Origin）。
- **影响：** 须重启 auth + 网关。已登录用户重新登录两边。见 [登录注册流程](/engineering/tech/auth-flows)、[接口契约](/engineering/process/contracts)。

## DEC-045 Session 超时走 yml，启动类不加 EnableRedis*Session

- **日期：** 2026-10-16
- **状态：** 已采纳
- **背景：** `SESSION_TIMEOUT=7d` 写在 yml，但 Redis 里 Session TTL 只有约 30 分钟。原因是 auth `@EnableRedisHttpSession`、网关 `@EnableRedisWebSession` 默认 `maxInactiveIntervalInSeconds=1800`，抢在 Boot 自动配置前面，环境变量进不去。
- **决策：** 两个启动类去掉上述注解，保留 `spring.session.store-type=redis` 与 `timeout: ${SESSION_TIMEOUT}`。Cookie 名、不 Base64、网关 `WebSessionIdResolver` 不变。已有 30 分钟 Session 不会自动变 7 天，须重启 auth+网关后重新登录。
- **替代：** 注解上写死秒数（否决：不再跟环境变量）；单独 Bean 再 setMaxInactiveInterval（否决：重复配置）。
- **影响：** 只改启动类与 yml 注释。见 [登录注册流程](/engineering/tech/auth-flows)、[接口契约](/engineering/process/contracts)。

## DEC-044 公开笔记先待审，运营通过后进 Feed

- **日期：** 2026-10-15
- **状态：** 已采纳
- **背景：** 创作者笔记管理已有「审核中 / 未通过」Tab，但发帖直接 `published` 进发现流。治理台需要审稿，不要另建 CMS。
- **决策：** 复用 `post.status`：公开发布写 `pending`；仅自己可见仍 `published`。运营 `POST /ops/posts/{id}/approve|reject`（Feign 内网 POST）。通过 → `published` 并写 Outbox；驳回必须填回复（运营弹窗，禁止浏览器 prompt），写 `rejected` + `reject_reason`，事务提交后投递通知 `review_reject`（`group=review`，preview 为回复）。作者可改待审/未通过，未通过再保存回到待审。仅自己改公开重新排队。已有已发布笔记保持 `published`。Feed / 他人主页仍只出 `published`。创作者 Tab 与首页待处理展示未通过回复。精细权限与 CMS 仍不做（DEC-034）。
- **替代：** 新表 `post_review`（否决：状态已在 `post.status`）；所有可见范围都审（否决：仅自己本来不进 Feed）；驳回不通知（否决：作者看不到原因）。
- **影响：** 已有库执行 `007_post_review.sql`，重启 content / moderation / notification。见 [API 明细](/engineering/process/api-endpoints)。

## DEC-043 运营用户详情可拆关注、删通知

- **日期：** 2026-10-15
- **状态：** 已采纳
- **背景：** 治理台用户页原先只有冻结和站内资料预览。需要看目标用户的关注/粉丝与收件箱，并有处置动作，不要空预览。
- **决策：** 员工 `GET /ops/users/{id}/following|followers`、`GET /ops/users/{id}/notifications`。取消「他关注谁」走 `POST .../following/{peerId}/remove`（userId=follower）；移除粉丝走 `POST .../followers/{peerId}/remove`（userId=followee）。删通知 `POST /ops/notifications/{id}/hide`，物理 DELETE 收件箱行（表无 `deleted_at`）。运营看别人收件箱不 `markRead`。moderation Feign user / notification，内网更新一律 POST。不新建表。精细权限与 CMS 仍不做（DEC-034）。创作者审稿后置。
- **替代：** 运营直连用户侧 `/users/{pageId}/following` 与 `/notifications`（否决：无按 userId 巡查、会标已读、不能拆别人的边）；软删通知列（否决：现网无该列）。
- **影响：** 重启 user / notification / moderation。见 [API 明细](/engineering/process/api-endpoints)。

## DEC-042 运营隐藏媒体写 media_asset.blocked_at，不删 MinIO

- **日期：** 2026-10-15
- **状态：** 已采纳
- **背景：** 媒体库第一刀只有列表预览。治理台需要能处置违规图，但不能物理删对象（帖子/私信仍引用 key）。
- **决策：** `POST /ops/media/{id}/hide|restore`。隐藏写 `blocked_at`；社区笔记详情与封面跳过该媒体；内网 `GET /internal/media/{id}` 对已隐藏返回 status=2，头像/私信图不能再绑定。隐藏头像/封面时 Feign user 清掉资料上的 object key。已发出私信图仍在消息 JSON 里，改走私信巡查隐藏该条。冻结上传人走已有接口。不删 MinIO。
- **替代：** 删 MinIO 对象（否决）；只读预览（否决，DEC-041 已补动作）。
- **影响：** 已有库执行 `006_ops_media_block.sql`，重启 content / user / moderation。见 [API 明细](/engineering/process/api-endpoints)。

## DEC-041 运营媒体库只读 media_asset，不删 MinIO

- **日期：** 2026-10-15
- **状态：** 已被 DEC-042 补充
- **背景：** 治理台按优先级接媒体库。上传元数据已在 content `media_asset`，字节在 MinIO。运营需要按用途巡查头像/封面/笔记/私信图，不要另建 CMS 资源库。
- **决策：** `GET /ops/media`（员工鉴权）Feign `GET /internal/media/ops`。按 id 倒序游标；`q` 只接受媒体 ID 或上传人 ID；`purpose` 取 object_key 前缀；`status` pending/ready/fail。已就绪才返回公开 URL。处置动作见 DEC-042。
- **替代：** 运营直扫 MinIO（否决：无归属/用途）；物理删除（否决：会打断已发帖和私信）。
- **影响：** 见 DEC-042。

## DEC-040 运营私信巡查：隐藏写 message.deleted_at

- **日期：** 2026-10-15
- **状态：** 已采纳
- **背景：** 治理台要先接私信巡查。用户侧已用 `deleted_at IS NULL` 过滤列表与未读。已有库没有该列。Feign 默认 JDK 客户端不能 PATCH。
- **决策：** 运营 `GET /ops/conversations`、`GET /ops/conversations/{id}/messages`、`POST /ops/messages/{id}/hide|restore`。moderation 员工鉴权后 Feign message 内网（也是 POST）。隐藏只对双方用户不可见，不物理删。搜 `q` 只接受数字（成员 userId 或会话 id）。冻结发送人走已有 `POST /ops/users/{id}/freeze`。媒体库/通知关注/审稿另做；精细权限与 CMS 仍不做（DEC-034）。
- **替代：** 运营直连公网 `/conversations`（否决：用户接口无全站列表、无隐藏）；新拆 admin（否决 DEC-034）。
- **影响：** 已有库执行 `onepage_message` 的 `002_ops_hide.sql`，重启 message + moderation。见 [API 明细](/engineering/process/api-endpoints)、[库表](/engineering/tech/database-schema)。

## DEC-039 运营工作台概览 + 目录 CRUD + 恢复必须写 null

- **日期：** 2026-10-15
- **状态：** 已采纳
- **背景：** 运营要做全站治理台。工作台只有入口卡；用户页必须先搜；分类/活动只读；笔记「恢复」接口成功但 `deleted_at` 仍在（MyBatis-Plus `updateById` 忽略 null）。首页 Tab 仍写死八个分类。
- **决策：** 工作台 `GET /ops/overview` 汇总社区用户、在线/下架笔记、评论、工单、目录条数。用户 `GET /ops/users` 可空 `q`，默认全表分页。分类/活动/话题 CRUD 在 content（`005_ops_catalog.sql`），运营经 Feign 写；社区 `GET /catalog` 驱动首页 Tab、发帖分类/活动/话题联想。恢复帖用 `LambdaUpdateWrapper.set(deletedAt, null)`。侧栏两级：治理（工单/用户/私信）、内容（笔记/评论/分类/活动/话题）。直播/搜索/AI 仍不进菜单（DEC-038）。CMS 上帝后台仍不做（DEC-034）。
- **替代：** 新拆 admin 服务（否决）；运营直接连 content 公网写接口（否决：员工鉴权在 moderation）。
- **影响：** 须执行 `005_ops_catalog.sql` 并重启 content / user / moderation / gateway。见 [API 明细](/engineering/process/api-endpoints)、[库表](/engineering/tech/database-schema)。

## DEC-038 运营菜单只挂当前真实能力

- **日期：** 2026-10-15
- **状态：** 已采纳
- **背景：** 运营侧栏只有工单和用户。产品要完整菜单，但直播 / 搜索 / AI 还没有后端。
- **决策：** `app-ops` 菜单 = 工作台 + 治理（举报工单、用户、笔记、评论）+ 目录（分类与活动，只读）。笔记列表/下架恢复、评论隐藏恢复、目录均走 `/api/v1/ops/**`，Feign content。没有服务的能力（直播、全文搜索、推荐位 CMS、AI）不进菜单、不做空壳。
- **替代：** 先铺一排占位页（否决：看起来完整、点进去没有）；把社区壳整站嵌进运营（否决：威胁模型不同）。
- **影响：** 见 [API 明细](/engineering/process/api-endpoints)、[前端结构](/engineering/frontend)。

## DEC-037 运营后台是完整治理台：站内预览 + 下架/恢复 + 封禁/解冻

- **日期：** 2026-10-15
- **状态：** 已采纳
- **背景：** 工单对象曾链到社区 `?note=`。待处理帖举报只露出忽略/下架，运营无法在站内审帖，也无法恢复或封禁作者。产品要求运营端是项目全局控制。
- **决策：** `app-ops` 是 **完整治理台**，不是 CMS。工单点对象标题在运营站弹层预览（`GET /ops/posts/{id}` 已下架也能看；用户 `GET /ops/users/{id}`），**不要跳社区**。`PATCH /ops/reports/{id}` action：`dismiss` / `takedown` / `restore` / `ban` / `unban`。帖举报的 `ban` = 下架该帖并冻结作者；`restore` 由 content 清 `deleted_at`。已处理工单仍可恢复/再处置（`dismiss` 除外）。分类、活动、推荐位仍不进 moderation 进程（DEC-034 的 CMS 非目标不变）。
- **替代：** 新开 admin 上帝服务（否决）；运营点开社区详情（否决：已下架帖社区 404，且离开工单上下文）。
- **影响：** 见 [API 明细](/engineering/process/api-endpoints)、[体验](/design/system/experience)。

## DEC-036 封禁账号走 auth 冻结，不走 user 库

- **日期：** 2026-10-15
- **状态：** 已采纳
- **背景：** DEC-034 曾写封禁由 user 执行。登录能否进站看的是 `auth_account.status`，资料在 user 库。两边各改会漂移。
- **决策：** 运营 `ban` / `POST /ops/users/{id}/freeze` Feign auth `status=1`；解冻 `unfreeze` 改回 `0`。user-service 不存封禁位。不能冻结员工名单或自己。
- **替代：** 在 `user_profile` 加封禁字段（否决：登录仍会放行）。
- **影响：** 契约与架构以 auth 为准。见 [API 明细](/engineering/process/api-endpoints)。

## DEC-035 运营登录强制 `OPS_STAFF_USER_IDS`，空名单拒绝

- **日期：** 2026-10-15
- **状态：** 已采纳
- **背景：** 运营站曾与社区共用登录，白名单为空时任意已登录用户可进 `/ops`。社区账号误进运营会下架/冻结。
- **决策：** 员工名单只放环境变量 `OPS_STAFF_USER_IDS`（userId 逗号分隔，不写死 Java）。空名单 = 无人可进。运营登录走 `POST /auth/login?audience=ops` 与 `POST /auth/sms/login?audience=ops`：非员工 403 且不种 Session；运营短信登录不自动开户。壳进站先 `GET /ops/me`。`/ops/**` 再拦一次。社区/创作者登录不加 `audience`，不受影响。
- **替代：** 前端一页号名单（否决：可绕）；空名单开发放行（否决：本机也会串站）；独立 admin 用户库（否决：DEC-034 不拆 admin 服务）。
- **影响：** 本机必须把员工 `userId` 填进 `.env` 并重启 auth / moderation / gateway。见 [API 明细](/engineering/process/api-endpoints)。

## DEC-034 第三站叫运营后台 `app-ops`，后端不拆 admin 服务

- **日期：** 2026-10-15
- **状态：** 已采纳（先定名与边界，P5 再建模块）
- **背景：** 产品前端已拆社区 `apps/shell`（:5174）和创作者 `apps/app-creator`（:5175）。还缺给内部人员审帖、处理举报、下架/封禁的界面。创作者中心是 C 端作者工作台，不能兼做运营。若再拆一个 `onepage-admin-service`，容易变成跨域写库的上帝服务。
- **决策：**
  - **前端第三站：** 中文 **运营后台**；目录 `OnePage-web/apps/app-ops`；包 `@onepage/app-ops`；本机 **5176**。独立站点、独立登录页（无注册），入口不挂社区侧栏。上线用内网或独立 host（如 `ops.`），不要和社区同域公开入口。
  - **后端不新建 `onepage-admin-service`。** 治理进程沿用路线图已有的 **`onepage-moderation-service`**（库 `onepage_moderation`）：举报单、处理记录、员工权限校验。下架帖由 **content** 执行；冻结/解冻账号由 **auth**（`status=1/0`），不要再写 user 库改登录态。网关给运营站单独前缀 **`/api/v1/ops/**`**，验 Session 且必须是员工（staff），不要只靠前端藏入口。
  - **员工身份：** 同一套 `ONEPAGE_SESSION`；员工以 `OPS_STAFF_USER_IDS` 为准（DEC-035，空名单拒绝）。禁止公开注册成员工。创作者审核 Tab 只是作者看自己的帖状态，不是运营后台。
  - **P5 范围：** 完整治理台（工单站内预览、忽略/下架/恢复、封禁/解冻作者，DEC-037）。分类、活动、推荐位等 CMS 仍放对应领域服务的 ops 接口，不另起 admin 进程。章程里「完整运营后台」指 CMS，不是指治理缺动作。
- **替代：** 把运营页塞进创作者站（否决：受众和威胁模型不同）；`apps/app-admin` + `onepage-admin-service`（否决：admin 与创作者「后台」混名，服务会变成聚合层）；只做 moderation 前端名叫 `app-moderation`（否决：以后不止举报）。
- **影响：** 端口规划 5173 文档 / 5174 社区 / 5175 创作者 / **5176 运营**。`CORS_ALLOWED_ORIGINS` 与 MinIO 源须含 5176。见 [路线图](/engineering/tech/mvp-roadmap)、[前端结构](/engineering/frontend)。

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

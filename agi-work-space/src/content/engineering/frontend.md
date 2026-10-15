---
title: 前端结构
description: OnePage-web 微前端与 agi-work-space 文档壳
order: 4
---

FE 维护。

## 两个前端

| 工程 | 说明 |
| --- | --- |
| **OnePage-web** | 用户产品；pnpm monorepo（社区壳 + 创作者独立站；P5 运营后台 `app-ops`；其余子应用待拆） |
| **agi-work-space**（本仓） | 文档工作空间；单 Vite SPA |

产品路由与状态以下表为准；本仓路由见 [工作空间写回约定](/engineering/process/workspace-writeback)。PC 壳视觉见 [视觉规范](/design/system/specs)。

## OnePage-web 结构

源码里有面向阅读的中文注释（文件角色、Vue/Pinia 关键字、为什么走 Cookie/代理/弹窗登录）。业务事实仍以本页和契约为准，不要把长文案只写在注释里。

P0 **已有**：`apps/shell`（社区站，本机 **5174**），`apps/app-creator`（创作者独立站，本机 **5175**，地址栏 favicon 与社区相同），`apps/app-ops`（运营后台，本机 **5176**；独立登录无注册，不挂社区导航），`packages/api-client`（`credentials: 'include'`，含 P1 发帖/Feed/赞藏评与 `/ops/reports` 方法）。三边 Vite 都把 `/api` 代理到网关（`VITE_DEV_PROXY_TARGET`），并把本机 `/onepage-media` 转到 MinIO `:9000`，避免运营站直传 CORS。PC 壳：190px 左栏 + 首页顶搜（其它页搜索图标）+ 瀑布流笔记。首页推荐接 `GET /feeds/explore`，关注 Tab 接 `/feeds/following`（已关注作者的公开帖）；旅行等 Tab 带 `category` 筛。自己的帖详情不显示关注钮；他人可关注/取关。点卡片打开 `NoteDetailModal`，拉 `GET /posts/{id}`、评论与赞藏。底栏赞/藏/发评走 `/reactions` `/comments`；点评论输入后四钮收起、出「发送」。发评输入 `@` 调 `GET /users/me/suggest`，列表浮在输入框上方，点选后带 `mentionPageIds`；评论里 @ 可点进 `/u/{pageId}`（昵称唯一时也能打开）。评论头像用 `author.avatarUrl`。视频页同接口只展示 `postType=video`。社区站已去掉 picsum / 假帖 / 假直播 / 假私信；通知页接真实收件箱；消息页接真实单聊、图片、笔记卡片与 `WS /ws/im`（`stores/imSocket.ts`）。详情分享弹层选会话/关注好友走 `POST /posts/{id}/share/im`。「我的」点获赞与收藏打开 `ReceivedListModal`。无接口的页改空态。搜索无结果为空；他人主页资料走 `GET /users/{pageId}`。文档工作空间占用 **5173**，不要和社区抢端口。三站地址栏 favicon 同一套**透明底红书** PNG（`favicon-32.png` / `favicon.png`）。产品壳 `BrandLogo` 用透明底锁套（开本书 + ONE 墨 PAGE 红），侧栏高 30px；深色主题切 `logo-dark.png`。资源与文档壳 `public/` 同步。

规划中尚未建：`app-feed`、`app-user`、`app-im`、`app-live`、`packages/ui`。运营后台壳与治理服务已接（DEC-035 强制员工名单）。

关键文件：`apps/app-ops/src/App.vue`（运营壳；治理/内容折叠菜单与创作者数据报表同结构）、`OpsHome.vue` + `OpsChart.vue`（ECharts）、`OpsReports.vue`、`OpsPosts.vue`、`OpsComments.vue`、`OpsMedia.vue`（媒体库只读预览）、`OpsMessages.vue`（私信巡查）、`OpsUsers.vue` + `OpsUserDetail.vue`（用户详情：关注/粉丝/收件箱，可拆边、删通知、冻结）、`OpsCategories.vue`、`OpsCampaigns.vue`（封面裁剪上传）、`OpsTopics.vue`、`OpsPager.vue`、`OpsDialog.vue`、`OpsTargetPreview.vue`、`GET /ops/me` 校验员工。首页 Tab 与发帖分类走 `GET /catalog`。`apps/app-creator/src/App.vue`（创作者壳；未登录整页 `CreatorLogin`）、`CreatorLogin.vue`（生活拼贴背景 + 右侧 20px 表单；仅短信/账密，无注册）、`CreatorNotes.vue` + `CreatorNotePreview.vue` + `notes.ts`（`GET /posts/mine`；悬停预览本页弹层；编辑进发布编辑页 `PATCH /posts/{id}`；视频首帧；纯文字 `TextNoteCover`）、`CreatorDash.vue`（最近笔记同接口）、`PublishComposer.vue`（落地只提示上传；图片先裁 3:4）、`PublishEditor.vue`（点上传后的编辑页，`createPost` / 改已有笔记 `patchPost`，带 original/contentKind/eventId/visibility；好友与定时拦截）、`CreatorEvents.vue` + `events.ts`。`apps/shell/src/App.vue`（侧栏；`?post=` 打开详情）、`components/AuthModal.vue`、`pages/MePage.vue`（展示页；编辑弹窗；封面/头像先裁剪再上传；笔记/收藏/点赞/历史 Tab；点关注粉丝出列表；点获赞与收藏出 ReceivedListModal）、`pages/UserPage.vue`（他人主页可关注/取关；笔记只出公开）、`components/FollowListModal.vue`、`components/ReceivedListModal.vue`、`components/ProfileNoteList.vue`、`components/MoreMenu.vue`（更多弹窗；已登录含设置/修改密码）、`stores/moreMenu.ts`、`stores/theme.ts`、`data/legal.ts`、`stores/authModal.ts`、`components/SearchBox.vue`（历史仅本机记录，直播/话题空）、`pages/SearchPage.vue`（筛选 UI + 空态）、`pages/YehYehPage.vue`、`stores/yehyeh.ts`（会话历史 localStorage；发送说明尚未接入）、`pages/HomePage.vue`、`components/NoteFeedCard.vue`（图/视频首帧；纯文字用 `TextNoteCover`）、`components/TextNoteCover.vue`（与创作者同款：封面居中特粗标题）、`components/NoteDetailModal.vue`（详情/评论/赞藏走接口；展示原创/类型/活动/仅自己；无图左侧同文字封面）、`stores/noteDetail.ts`、`pages/VideoPage.vue`（无分类视频瀑布流）、`pages/LivePage.vue`（分类 Tab + 空态）、`pages/NotifyPage.vue`、`pages/MessagesPage.vue`、`pages/AboutPage.vue`、`pages/LegalPage.vue`、`pages/HelpPage.vue`、`pages/CreatorPage.vue`（跳转到创作者站点）、`src/creatorSite.ts`、`pages/PartnerPage.vue`、`pages/ShortcutsPage.vue`、`components/Icon.vue`、`data/home.ts`（卡片类型与 Tab，无假 NOTES）。未登录时侧栏末项为红按钮「登录」，点开 `AuthModal`；登录后恢复「我的」。点「更多」开弹窗（无遮罩）；菜单项在弹窗内三级页，不进独立路由。`/privacy` 等路由仍保留可直达。

```text
apps/shell/           社区站：布局、登录、注册、当前用户、PC 首页壳（:5174）
apps/app-creator/     创作者中心（:5175）：C 端作者工作台；未登录独立登录页；首页/笔记/报表/活动/字典/发布
apps/app-ops/         运营后台（:5176）：内部员工；独立登录无注册；工作台/工单/用户/私信/笔记/评论/媒体库/分类/活动/话题
apps/app-feed/        发现、发帖、详情、评论（规划）
apps/app-user/        个人主页、资料、关系列表（规划）
apps/app-im/          私信（规划）
apps/app-live/        直播（P4）
packages/ui/          shadcn-vue（待抽）
packages/api-client/  fetch + Cookie
```

## 路由（产品）

| 路径 | 子应用 |
| --- | --- |
| `/` | shell（首页已接 Feed；顶栏居中完整搜索条） |
| `/me` | 无顶栏；封面通栏铺到 Tab 分割线；搜索与编辑、退出登录同一组白字胶囊；空态插画 `empty-me.png` |
| `/u/:pageId` | 无顶栏；他人个人主页（资料接 `GET /users/{pageId}`） |
| `/search` | 全局搜索：左分类（笔记/用户/话题/直播），右耶耶入口；分类行最右筛选胶囊。无搜索服务，结果空态 |
| `/yehyeh` | 耶耶：无顶搜；249px 历史栏、825×150 白底输入卡、对话页；搜索 `q` 开聊。AI 未接入，回复说明暂不可用 |
| `/login`, `/register` | 不再渲染独立页；守卫打开 `AuthModal` 后回到 `/` |
| `/video` | 视频瀑布流：与首页同卡片 280×440，无分类；只展示视频帖（右上播放图标，无时长） |
| `/live` | 直播：分类 Tab 同首页；live 服务未建，空态插画 `empty-live.png` |
| `/notify` | 通知：居中三 Tab 接 `GET /notifications?group=`；评论摘要；侧栏与 Tab 未读数 |
| `/messages` | 私信：无顶栏、无搜索；左 320px 会话栏 + 右侧对话；`?c=` 打开会话；登录后 `WS /ws/im`；断线约 3 秒轮询；可发图片；`post_card` 出封面卡片，点开详情 |
| `/about` | 关于我们：可拖装饰卡；协议/隐私/帮助/创作者弹窗 |
| `/create` | 侧栏发布选中；主区同首页；点击时新开创作者站 |
| `/creator` | 更多入口：新开创作者站后回首页 |
| `/privacy`, `/terms` | 隐私政策 / 用户协议（`data/legal.ts`） |
| `/help` | 帮助与客服：反馈文字、图片、联系方式；P0 写入 localStorage |
| `/creator` | 社区站只做跳转，整页进入创作者站点 `http://localhost:5175` |
| `/partner/live`, `/partner/verify` | 主播入驻 / 大 V 认证申请 |
| `/shortcuts` | 键盘快捷键 |
| `/settings` | shell（待做） |
| `/explore`, `/following`, `/post/:id` | app-feed（P1） |
| `/u/:pageId`, `/u/:pageId/*` | 现由 shell `UserPage` 承接；关系列表仍待 app-user |
| `/messages/:id` | app-im |
| `/live/:roomId` | app-live |

## 状态（产品）

| 名称 | 范围 | 来源 |
| --- | --- | --- |
| 当前用户 | 壳 | `GET /api/v1/users/me`（Cookie Session） |
| 未读通知/私信 | 壳 | `GET /notifications/unread-count` 与 `GET /conversations/unread-count`；登录后 `WS /ws/im` 推到会刷新未读。消息页在线不轮询 |
| 主题/语言 | 壳 | localStorage |

子应用不跨 import 业务页面，只使用 `packages/*`。

## 本地怎么起

自己起两个产品前端（不要用文档仓的 5173）：

1. 社区：`pnpm --filter @onepage/shell dev` → **http://localhost:5174**
2. 创作者：`pnpm --filter @onepage/app-creator dev` → **http://localhost:5175**
3. 运营后台：`pnpm --filter @onepage/app-ops dev` 或根目录 `pnpm dev:ops` → **http://localhost:5176**。不挂社区导航，用内部地址打开。登录带 `audience=ops`；须在 `.env` 填 `OPS_STAFF_USER_IDS`（空则谁也进不去）。本机 `.env` 的 `CORS_ALLOWED_ORIGINS` 须含 5176（网关已启动则改完要重启）。

在 5174 点「更多 → 创作者中心」会 **新开标签** 打开 5175，社区页仍留在原标签。创作者主区无品牌标题，右上仅头像菜单；首页、发布、笔记管理、数据报表、活动中心、创作字典都是 20px 白卡片并留右边、下边页边。根目录 `pnpm dev` 可一次起社区和创作者（不含运营）。

## 本仓（文档壳）摘要

| 路径 | 页面 |
| --- | --- |
| `/` | 项目首页 |
| `/settings` | 配置中心 |
| `/{slug}` | Markdown 文档。`/space/harness` 为目录预览；`/space/harness/*` 叶子文档有角色时直接进编辑，不先预览 |

组件与写回交互见历史模版段落；实现以 `src/views/*` 为准。入口 `index.html` 引用 `public/favicon-32.png` 等，与 `OnePage-web` 社区站相同。顶栏 Logo 右侧为 `Workspace` 标识。侧栏「文档目录」右侧显示篇数与加号：点加号可选新建文档或子目录（有角色权限时；默认建在当前可写目录，否则 `start`）。

## 无障碍与交互

产品站遵循 shadcn 可访问性；文档壳保持键盘导航与主题切换。设计稿链接待 UIUE 填入 [设计交付](/design/handoff)。

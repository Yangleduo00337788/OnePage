---
title: 前端结构
description: OnePage-web 微前端与 agi-work-space 文档壳
order: 4
---

FE 维护。

## 两个前端

| 工程 | 说明 |
| --- | --- |
| **OnePage-web** | 用户产品；pnpm monorepo（社区壳 + 创作者独立站；其余子应用待 P1） |
| **agi-work-space**（本仓） | 文档工作空间；单 Vite SPA |

产品路由与状态以下表为准；本仓路由见 [工作空间写回约定](/engineering/process/workspace-writeback)。PC 壳视觉见 [视觉规范](/design/system/specs)。

## OnePage-web 结构

源码里有面向阅读的中文注释（文件角色、Vue/Pinia 关键字、为什么走 Cookie/代理/弹窗登录）。业务事实仍以本页和契约为准，不要把长文案只写在注释里。

P0 **已有**：`apps/shell`（社区站，本机 **5174**），`apps/app-creator`（创作者独立站，本机 **5175**，地址栏 favicon 与社区相同），`packages/api-client`（`credentials: 'include'`，含 P1 发帖/Feed/赞藏评方法）。两边 Vite 都把 `/api` 代理到网关（`VITE_DEV_PROXY_TARGET`）。PC 壳：260px 左栏 + 顶搜 + 瀑布流笔记。首页推荐/关注接 `GET /feeds/explore` 与 `/feeds/following`（P1 同一数据）；旅行等 Tab 带 `category` 筛。自己的帖详情不显示关注。点卡片打开 `NoteDetailModal`，拉 `GET /posts/{id}`、评论与赞藏。底栏赞/藏/发评走 `/reactions` `/comments`；评论头像用 `author.avatarUrl`。视频页同接口只展示 `postType=video`。搜索页仍占位。文档工作空间占用 **5173**，不要和社区抢端口。三站地址栏 favicon 同一套 Logo PNG（`favicon-32.png` / `favicon.png`）。

规划中尚未建：`app-feed`、`app-user`、`app-im`、`app-live`、`packages/ui`。

关键文件：`apps/app-creator/src/App.vue`（创作者壳；未登录整页 `CreatorLogin`）、`CreatorLogin.vue`（生活拼贴背景 + 右侧 20px 表单；仅短信/账密，无注册）、`CreatorNotes.vue` + `notes.ts`、`PublishComposer.vue`（落地只提示上传）、`PublishEditor.vue`（点上传后的编辑页，走 `api.createPost`）、`CreatorEvents.vue` + `events.ts`。`apps/shell/src/App.vue`（侧栏）、`components/AuthModal.vue`、`pages/MePage.vue`（展示页；编辑弹窗；封面/头像上传；笔记/收藏/点赞/历史 Tab 空态）、`pages/UserPage.vue`（他人主页 `/u/:pageId`，P0 作者占位）、`components/MoreMenu.vue`（更多弹窗；已登录含设置/修改密码）、`stores/moreMenu.ts`、`stores/theme.ts`、`data/legal.ts`、`stores/authModal.ts`、`components/SearchBox.vue`、`pages/SearchPage.vue`、`pages/YehYehPage.vue`、`stores/yehyeh.ts`（会话历史 localStorage）、`pages/HomePage.vue`、`components/NoteFeedCard.vue`（图/视频首帧/纯文字封面）、`components/NoteDetailModal.vue`、`stores/noteDetail.ts`、`pages/VideoPage.vue`（无分类视频瀑布流）、`pages/LivePage.vue`（直播分类+在播卡片）、`pages/NotifyPage.vue`、`data/notify.ts`、`pages/MessagesPage.vue`、`data/im.ts`、`pages/AboutPage.vue`、`pages/LegalPage.vue`、`pages/HelpPage.vue`、`pages/CreatorPage.vue`（跳转到创作者站点）、`src/creatorSite.ts`、`pages/PartnerPage.vue`、`pages/ShortcutsPage.vue`、`components/Icon.vue`、`data/home.ts`、`pages/ComingSoonPage.vue`。`data/notes.ts` 勿用。未登录时侧栏末项为红按钮「登录」，点开 `AuthModal`；登录后恢复「我的」。点「更多」开弹窗（无遮罩）；菜单项在弹窗内三级页，不进独立路由。`/privacy` 等路由仍保留可直达。

```text
apps/shell/           社区站：布局、登录、注册、当前用户、PC 首页壳（:5174）
apps/app-creator/     创作者后台（:5175）：未登录独立登录页；登录后首页/笔记/报表/活动/字典/发布；头像退出
apps/app-feed/        发现、发帖、详情、评论（P1）
apps/app-user/        个人主页、资料、关系列表（P1–P2）
apps/app-im/          私信（P2）
apps/app-live/        直播（P4）
packages/ui/          shadcn-vue（待抽）
packages/api-client/  fetch + Cookie
```

## 路由（产品）

| 路径 | 子应用 |
| --- | --- |
| `/`, `/me` | shell（首页 `/` 已接 Feed） |
| `/u/:pageId` | 他人个人主页（P0 先挂 shell；资料与笔记占位） |
| `/search` | 全局搜索：左分类（笔记/用户/话题/直播），右耶耶摘要；分类行最右筛选胶囊（悬停展开+灰选中，默认无灰；条件选中红底）；问耶耶 → `/yehyeh?q=` |
| `/yehyeh` | 耶耶：无顶搜；249px 历史栏、825×150 白底输入卡、对话页；搜索 `q` 直接开聊（P0 mock） |
| `/login`, `/register` | 不再渲染独立页；守卫打开 `AuthModal` 后回到 `/` |
| `/video` | 视频瀑布流：与首页同卡片 280×440，无分类；只展示视频帖（右上播放图标，无时长） |
| `/live` | 直播瀑布流：分类 Tab 同首页（全部/游戏/才艺直播/生活分享/兴趣爱好/科技新闻/运动户外）；卡片 280×440，右上动态「直播中」，头像在播点，右侧人数 |
| `/notify` | 通知：居中三 Tab（评论和@ / 点赞和收藏 / 新增关注），P0 占位列表 |
| `/messages` | 私信：左 320px 会话列表，点开右侧对话；`?id=` 记住当前会话。P0 占位 |
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
| 未读通知/私信 | 壳 | 各自 API + WS 推送 |
| 主题/语言 | 壳 | localStorage |

子应用不跨 import 业务页面，只使用 `packages/*`。

## 本地怎么起

自己起两个产品前端（不要用文档仓的 5173）：

1. 社区：`pnpm --filter @onepage/shell dev` → **http://localhost:5174**
2. 创作者：`pnpm --filter @onepage/app-creator dev` → **http://localhost:5175**

在 5174 点「更多 → 创作者中心」会 **新开标签** 打开 5175，社区页仍留在原标签。创作者主区无品牌标题，右上仅头像菜单；首页、发布、笔记管理、数据报表、活动中心、创作字典都是 20px 白卡片并留右边、下边页边。根目录 `pnpm dev` 可一次起这两个。

## 本仓（文档壳）摘要

| 路径 | 页面 |
| --- | --- |
| `/` | 项目首页 |
| `/settings` | 配置中心 |
| `/{slug}` | Markdown 文档。`/space/harness` 为目录预览；`/space/harness/*` 叶子文档有角色时直接进编辑，不先预览 |

组件与写回交互见历史模版段落；实现以 `src/views/*` 为准。入口 `index.html` 引用 `public/favicon-32.png` 等，与 `OnePage-web` 社区站相同。顶栏 Logo 右侧为 `Workspace` 标识（`AppHeader.vue`）。侧栏「文档目录」右侧显示篇数与加号：点加号可选新建文档或子目录（有角色权限时；默认建在当前可写目录，否则 `start`）。

## 无障碍与交互

产品站遵循 shadcn 可访问性；文档壳保持键盘导航与主题切换。设计稿链接待 UIUE 填入 [设计交付](/design/handoff)。

# AGENTS.md

若你从父目录 `OnePage/AGENTS.md` 链式读到本文：继续遵守本文；`java-alibaba.md` 应已并行读取。不要再重复读父目录 AGENTS.md。

你正在 **ONEPAGE（一页）** 的 **agi-work-space** 仓库里工作。本仓 = **项目知识库 + 文档 Vue 壳**；用户产品代码在同级目录 `OnePage-web`、`OnePage-Server`。先读完本文件再改任何文件。

不要改角色 ID、不要为每篇文档新建 Vue 页面。业务 API 与微服务在 Server 仓实现，不在本仓堆业务 Controller。

## 1. 身份与目标

| 项 | 当前值（换项目时改这里） |
| --- | --- |
| 工作空间 | ONEPAGE（一页） |
| 一句话 | 记录生活每一页。 / Every Day, A New Page |
| 本仓形态 | 文档壳 + `src/content` 知识空间（长期事实源；`npm run dev` 预览） |
| 产品代码仓 | `../OnePage-web`（FE）、`../OnePage-Server`（BE） |
| 架构事实源 | `src/content/engineering/tech/mvp-roadmap.md` |
| 文档事实来源 | `src/content/**/*.md` |
| Agent 约束文件 | 本文件与 `src/content/space/harness/AGENTS.md` 正文同步；Cursor 打开 `OnePage/` 时还读父目录 `../AGENTS.md`。**不要**把可携带规则写进 `.cursor/rules` |

**你要做的：** 按用户请求改 **文档** 或 **文档壳** 代码，遵循 `src/content` 中的范围与 [接口契约](/engineering/process/contracts)（产品 API）及 [工作空间写回约定](/engineering/process/workspace-writeback)。写 MD 时遵守 [文档怎么写](/engineering/process/doc-style)（开头直接写作用，先短段再表格）。**知识空间目标：** 用 Agent 开发产品时，架构 / 功能 / 接口 / 表 / 视觉 / 决策 / 阶段必须同一轮沉淀进 `src/content`。若干年后人或 Agent 先读工作空间即可掌握项目全貌；聊天不是事实源。

**你不要做的：** 在本仓实现 ONEPAGE 业务后端/发帖 API；不要把用户产品整站塞进本仓路由。

## 1b. 可携带约束（换机）

团队规则只放 Git 里的 Markdown：父目录 `OnePage/AGENTS.md`、本文件、`space/harness/java-alibaba.md`。`.cursor/` 已被 gitignore，换机不会出现，**禁止**把回写表或 Java 必读写进 `.mdc`。

改 `OnePage-web` 或 `OnePage-Server`（以及改架构/功能约定）时，**同一轮回复内**把结论写进 `src/content`。不要等用户再提醒。不要只改 Vue/Java。表里没有对上的类型：补最接近的专文，并在 `status.md` 记一行。

| 改了什么 | 回写到 |
| --- | --- |
| 功能、范围、需求 | `product/spec/prd.md`；范围变了再改 `project/charter/` |
| 架构、服务边界、分期 | `engineering/tech/architecture.md`、`mvp-roadmap.md`、`domain.md`；流程用 `engineering/tech/diagrams.md` |
| 流程 / 时序 | `engineering/tech/diagrams.md`、`auth-flows.md` |
| 颜色、间距、导航、图标、搜索、卡片状态 | `design/system/specs.md`；交互补 `design/system/experience.md`；走查补 `design/handoff.md` |
| 路由、壳组件、占位页 | `engineering/frontend.md` |
| API / 鉴权 | `engineering/process/contracts.md`、`engineering/process/api-endpoints.md` |
| 表结构 | 先 `engineering/tech/database-schema.md` 与 `engineering/tech/sql/`（文档名必须与 Server `*.sql` 同名），再 Server `db/sql/` |
| 选型、走查拍板 | `records/decisions.md`（新 DEC 写在最上面） |
| 阶段进展 | `project/governance/status.md` 近期变更 |

静态原型只定分区。像素与文案以视觉规范为准。改 PC 壳先改规范再改（或同时改）`apps/shell`。

## 2. 动手前必读（按顺序）

1. 本文件 `AGENTS.md`
2. `src/config/workspace-roles.json` — 当前勾选了哪些角色（配置中心勾选会立刻写入此文件）
3. `src/content/space/references.md` — 空间如何引用项目
4. `src/content/engineering/tech/mvp-roadmap.md` — 微服务、MVP 分期
5. `src/content/project/charter/overview.md` — 范围与非目标
6. `src/content/project/governance/roles.md` — 七角色与可写目录
7. 若改产品接口：`src/content/engineering/process/contracts.md`
8. 若改文档壳：`src/content/engineering/process/workspace-writeback.md`、`src/content/engineering/frontend.md`
9. 若改 `OnePage-Server`（Java / Mapper / 服务 yml）：先读完 `src/content/space/harness/java-alibaba.md`，按其中「必须」写代码

用户指定了某一篇文档时，再打开对应 `src/content/...` 文件。不要在没读范围的情况下大范围重构。

## 3. 仓库地图

```
AGI-WorkSpace/
├── AGENTS.md                          ← Agent 入口约束（根目录副本）
├── .agi-workspace.local.json         ← 令牌，禁止提交
├── .gitignore
├── package.json
├── vite.config.ts                    ← 含 Git 远程代理
├── src/
│   ├── content/                      ← 文档树（会出现在侧栏「文档目录」）
│   │   ├── index.md                  ← 项目首页（导航挂在「开始阅读」下）
│   │   ├── start/                    ← 开始阅读
│   │   ├── project/ product/ design/
│   │   ├── engineering/ quality/
│   │   ├── delivery/ records/
│   │   └── space/                    ← 空间配置文档（不进「文档目录」树）
│   │       ├── references.md         ← 项目引用
│   │       └── harness/
│   │           ├── index.md          ← Harness 管理入口
│   │           ├── AGENTS.md          ← 本文件的带 frontmatter 版本
│   │           └── java-alibaba.md    ← Java 规约（改 Server 必读）
│   ├── lib/roles.ts                  ← 七角色 ID 与可写 slug，禁止删 ID
│   ├── lib/content.ts                ← MD 加载；`catalog.docsNavTree` 排除 `space`
│   ├── lib/git-sync.ts               ← 按 .gitignore 整仓 commit/push
│   ├── config/workspace-roles.json   ← 当前勾选角色（配置中心实时写入）
│   ├── views/SettingsView.vue        ← 配置中心（含工作空间标识）
│   └── views/DocView.vue             ← 唯一文档页
└── dist/ node_modules/               ← 忽略，禁止提交
```

## 4. 信息架构（侧栏）

**文档目录（`catalog.docsNavTree`）**

- 开始阅读（项目首页、项目介绍、新范式、WorkSpace 指南）
- 项目总览 / 产品设计 / 体验设计 / 研发设计 / 质量保障 / 交付运维 / 过程记录

**空间配置（侧栏单独一组，不进文档树）**

1. **配置中心** `/settings`：角色范围、工作空间标识（名称 / Logo / 口号）、本地工作目录、代码空间、推送成功文案
2. **项目引用** `/space/references`
3. **Harness 管理** `/space/harness` → 维护 `AGENTS.md` 与 [Java 开发规约](/space/harness/java-alibaba)

禁止把 `src/content/space/` 做进「文档目录」导航。`src/lib/content.ts` 里必须保持 `catalog.docsNavTree` 过滤 `id === 'space'`。授权本地工作目录后，导航以磁盘上的 Markdown 为准（窗口重新聚焦会再扫一遍），不要只依赖构建时打包的文件。

## 5. 七个角色（ID 锁定）

角色 ID 写死在 `src/lib/roles.ts`，与配置中心勾选一一对应。换项目只改对接人和 Markdown 正文。

| ID | 短名 | 可写 slug（含子路径） |
| --- | --- | --- |
| `rd` | RD | `engineering`（`engineering/frontend` 归 FE） |
| `fe` | FE | `engineering/frontend` |
| `pm` | PM | `product`、`product/*`、`project/charter` |
| `qa` | QA | `quality`、`quality/*` |
| `op` | OP | `delivery/ops` |
| `uiue` | UIUE | `design`、`design/*` |
| `pom` | POM | `delivery`、`delivery/management`、`project/governance`、`records`、`records/*` |

额外规则：

- 当前已启用角色以 `src/config/workspace-roles.json` 的 `selected`（或 `roles[].enabled`）为准，不要用浏览器 localStorage 猜测。
- 只允许在已勾选角色的职责范围内改文档或做对应开发。未勾选的范围仍可读，但禁止修改。
- 用户请求落在未勾选角色上时：提示「请先在 Workspace 配置中心勾选对应角色」，然后停止对该范围的修改。
- `selected` 为空时：不要改任何角色目录下的文档或对应实现，先请用户勾选。
- `src/content/space/**`：只要勾选了任意角色，即可在应用内编辑。
- `src/content/start/**` 与根目录 `index.md`（项目首页）：任意已选角色可读可改。
- 不要擅自把某篇文档的 slug 加进另一个角色，除非用户明确要求。
- 不要新增第八个角色，除非用户明确要求并同时改 `roles.ts`、配置中心文案、`project/governance/roles.md`。

## 6. 文档写法

新增分类：新建文件夹 + `index.md`，可多层嵌套（侧栏「新建子目录」，文件名也可写成 `auth/login`）。  
新增文档：新建 `.md`，frontmatter 必填：

```yaml
---
title: 标题
description: 一句话
order: 10
---
```

- 用户可见中文正文；占位写成「换成当前项目」，不要留英文 lorem。
- 业务上下文写在 Markdown，不要堆在 Vue 注释里。
- 接口 / 领域 / 决策变更当天同步对应 MD。
- **改 OnePage-web / OnePage-Server 的同一轮必须回写本仓文档**，禁止只改代码。功能 → PRD；架构/分期 → architecture / mvp-roadmap / domain；流程 → diagrams；视觉 → `design/system/specs.md`（交互/走查补 `experience.md`、`handoff.md`）；路由与壳 → `engineering/frontend.md`；接口 → 契约；表 → schema + `engineering/tech/sql/`；拍板 → `records/decisions.md`；进展 → `project/governance/status.md`。
- 改表：先改 `src/content/engineering/tech/database-schema.md` 与 `src/content/engineering/tech/sql/`（**文档文件名必须与 Server `*.sql` 同名**，禁止自编 `onepage_auth.sql`），再改 `OnePage-Server/*/db/sql/`。DDL 必须带表/列注释。
- 改表：先改 `src/content/engineering/tech/database-schema.md` 与 `src/content/engineering/tech/sql/`，再改 `OnePage-Server/*/db/sql/`（脚本必须有表/列注释）。
- **禁止**为单篇文档增加 `views/*.vue` 或新路由组件；一律走 `DocView` + `/:slug(.*)`。
- 有角色权限且已授权目录时：可在应用内新建 / 删除 Markdown（根目录 `index.md` 不可删）。
- 编辑器粘贴的图片写到该文档目录下的 `assets/`，用相对路径引用。

保存 AGENTS.md（slug `space/harness/AGENTS`）时：必须同步本仓根目录 `AGENTS.md`（无 YAML frontmatter，仅正文）。应用内 `DocView` 已按此处理。回写表或「先读 Java 规约」若有改动，还要同步父目录 `OnePage/AGENTS.md`（Cursor 工作区入口）。

## 7. 代码约定

- Vue 3 + Vite + TypeScript + Tailwind。用户可见壳子文案走 `src/i18n/messages.ts`。
- 改布局、主题时沿用现有 token，不引入第二套颜色系统。
- 文件与组件英文命名。
- Java（`OnePage-Server`）：遵守 [Java 开发规约](/space/harness/java-alibaba)（章节对齐阿里手册，正文为本项目约定；禁止把官方手册全文贴进仓库）。
- 本地 Git 推送走 isomorphic-git + 授权的工作目录；localhost 下 Git HTTP 走 Vite `/__git-remote/*` 代理。
- 推送必须尊重 `.gitignore`，一次 commit、一次 push，禁止按文件循环打 Contents API。
- 令牌只存在 `.agi-workspace.local.json`；推送时跳过 `*.local.json`。若该文件曾被误提交，应从索引中移除，不得再加入。

常用命令：

```bash
npm install
npm run dev
npm run build
```

没有用户明确要求时，不要 `git commit` / `git push`，不要改 git config，不要 force push。

## 8. 密钥与忽略文件（硬性）

**禁止读取、打印、提交、写入文档的内容：**

- `.agi-workspace.local.json` 及任何 `*.local.json`
- `.env`、`.env.*`（`.env.example` 除外）
- `*.pem` `*.key` `*.p12` `*.pfx`
- 访问令牌、密码、Cookie

**禁止提交：** `node_modules/`、`dist/`、`coverage/`、日志、编辑器垃圾文件（见根目录 `.gitignore`）。

发现令牌出现在聊天、URL、commit 或 Markdown 里时：不要复述令牌全文；提醒用户作废并换新。

## 9. Git 与远程

- 文档目录配置（默认 `src/content`）只决定网页保存路径，**不是**推送范围。
- 「提交并推送」= 按 `.gitignore` 暂存整个工作区 → 一次 commit → 一次 push。
- 未经用户要求不要执行 push。
- 不要把代理路径、本机文件夹授权提示写进远程 README 当密钥说明。

提交说明风格（与工程规范一致，团队可改）：`feat:` / `fix:` / `docs:` 开头，写原因。

## 10. 工具使用边界

- 只改与当前用户请求相关的文件，不做顺手大重构。
- 不要安装无关依赖。需要新依赖时先说明为什么。
- 不要编写攻击、漏洞利用、恶意软件；安全相关只给加固建议。
- 不要生成或传播未成年人性内容。
- 浏览器 / File System Access 权限失败时：引导用户到配置中心授权工作目录，不要伪造写入成功。

## 11. 典型任务怎么做

| 用户意图 | 正确做法 |
| --- | --- |
| 改某篇项目文档 | 改对应 `src/content/**/*.md`，保留 frontmatter |
| 改产品 UI / 接口 / 表 / 架构 / 功能 | 改 `../OnePage-web` 或 `../OnePage-Server`，**同一轮**回写上表对应 MD（知识空间为长期事实源） |
| 改 Java 后端 | 先读 `space/harness/java-alibaba.md`，再改 `OnePage-Server` |
| 换项目 | 改概述/目标/状态/角色对接人/本文件「当前值」表，不改壳子结构 |
| 加一篇文档 | 优先在应用内按角色新建；或新 md + frontmatter；若新分类则加文件夹和 index.md |
| 改侧栏空间配置 | `AppSidebar` + `space-nav.ts` + 对应 view/md |
| 改推送成功文案 | `src/config/push-success.json` 或配置中心「推送成功文案」 |
| 改名称 / Logo / 口号 | `src/config/branding.json`、`public/logo-*.png` 或配置中心「工作空间标识」 |
| 改角色可写范围 | 同时改 `src/lib/roles.ts` 与 `src/content/project/governance/roles.md` |
| 看当前勾选了谁 | 读 `src/config/workspace-roles.json`，不要读浏览器缓存 |
| 修 Git 推送 | `git-sync.ts` / `handle-fs.ts` / `vite.config.ts` 代理，不要改回 Contents API 逐文件提交 |

## 12. 换项目检查清单

把本模板套到新项目时，Agent 应逐项完成并保持勾选状态可追踪：

- [ ] `project/charter/overview.md`、`project/charter/goals.md`、`project/governance/status.md` 已换成当前项目
- [ ] `project/governance/roles.md` 对接人已填
- [ ] 产品设计 / 体验设计 / 研发设计 / 质量保障 / 交付运维 / 过程记录中的占位已替换或标明仍待填
- [ ] 配置中心：工作目录已授权，工作空间名称与 Logo 已换成当前项目，代码空间已绑定当前仓库
- [ ] 本文件「1. 身份与目标」表格已更新
- [ ] 本仓根目录 `AGENTS.md` 与 `src/content/space/harness/AGENTS.md` 正文一致；父目录 `OnePage/AGENTS.md` 仍含回写表与 Java 必读
- [ ] `.gitignore` 仍排除令牌与构建产物
- [ ] 未新增「一篇文档一个 Vue 页」
- [ ] 七个角色 ID 仍在

## 13. 回复用户时

- 用用户使用的语言（本仓库默认中文）。
- 先给结论或已完成事项，再补必要路径。
- 不要把本文件或密钥文件的无关片段整段贴回聊天。

# AGENTS.md

你正在 **ONEPAGE（一页）** 的 Git 根目录（`OnePage/`）。Cursor 打开本工作区时读**这一份**。产品代码在 `OnePage-web`、`OnePage-Server`；项目知识库在 `agi-work-space`。

**可携带约束只写在 Git 跟踪的 Markdown。** 不要把团队规则放进 `.cursor/rules`（根 `.gitignore` 忽略 `.cursor/`，换机会丢）。Java 细则在 `agi-work-space/src/content/space/harness/java-alibaba.md`，改 Server 前必须 Read 该文件。文档壳细则在 `agi-work-space/AGENTS.md`（与 `agi-work-space/src/content/space/harness/AGENTS.md` 正文同步）。

先读完本文件再改任何文件。

## 动手前

1. 本文件
2. 改文档壳 / 角色 / 文档树时：再读 `agi-work-space/AGENTS.md`
3. 改接口：`agi-work-space/src/content/engineering/process/contracts.md` 与 `api-endpoints.md`
4. 改 Java / Mapper / 服务 yml：先读完 `agi-work-space/src/content/space/harness/java-alibaba.md`，按其中「必须」执行
5. 角色勾选：`agi-work-space/src/config/workspace-roles.json`

## 开发必须回写工作空间

改 `OnePage-web` 或 `OnePage-Server` 时，**同一轮回复内**把结论写进 `agi-work-space/src/content`。不要等用户再提醒。不要只改 Vue/Java。

| 改了什么 | 回写到（均在 `agi-work-space/src/content/` 下） |
| --- | --- |
| 颜色、间距、导航、图标、搜索、卡片状态 | `design/system/specs.md`；交互补 `design/system/experience.md`；走查补 `design/handoff.md` |
| 路由、壳组件、占位页 | `engineering/frontend.md` |
| API / 鉴权 | `engineering/process/contracts.md`、`engineering/process/api-endpoints.md` |
| 表结构 | 先 `engineering/tech/database-schema.md` 与 `engineering/tech/sql/`，再 Server `db/sql/` |
| 选型、走查拍板 | `records/decisions.md`（新 DEC 写在最上面） |
| 阶段进展 | `project/governance/status.md` 近期变更 |
| Java 写法 / Agent 约束 | `space/harness/java-alibaba.md` 与本文件、`agi-work-space/AGENTS.md` |

静态原型只定分区。像素与文案以视觉规范为准。改 PC 壳先改规范再改（或同时改）`OnePage-web/apps/shell`。

## 代码约定（摘要）

- 前端：Vue 3 + TypeScript + Vite；用户可见文案走 i18n；不引入第二套颜色。
- Java：遵守 `java-alibaba.md`。`@PathVariable` 必须写名字。内网身份只用网关 `X-User-Id`。yml 只写 `${变量}`，机密不进仓库。
- 改表先改工作空间 DDL 文档，脚本带表/列注释。
- 没有用户明确要求时，不要 `git commit` / `git push`，不要改 git config，不要 force push。

## 密钥

禁止读取、打印、提交：`.env`、`.agi-workspace.local.json`、`*.local.json`、密钥与证书。令牌出现在聊天或文档里时不要复述全文。

## 回复

用用户的语言（默认中文）。先给结论，再补必要路径。

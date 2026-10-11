---
title: 工程规范
description: 目录、提交、分支、评审与文档同步
order: 2
---

RD 维护。代码和文档的共同规矩写在这里。

## 文档

- 写法见 [文档怎么写](/engineering/process/doc-style)
- 业务上下文只写在 `src/content/`，不写进 Vue 组件注释里充数
- 新增分类 = 新建文件夹 + `index.md`
- 新增文档 = 新建 `.md` + frontmatter（`title` / `description` / `order`）
- 接口、领域、决策变更当天同步对应 MD

## 代码

- 文件与组件用英文命名；用户可见文案走 i18n 或 Markdown
- 不要为单篇文档增加路由组件
- 改布局、主题时沿用现有 visual token，不引入第二套颜色
- Java 全文：[Harness · Java 开发规约](/space/harness/java-alibaba)（改 Server 前 Agent 必读）。要点：`@PathVariable("id")` 必须带名字；parent 已开 `-parameters`。本机 Nacos Discovery 地址见 [架构](/engineering/tech/architecture)（环境变量 `NACOS_SERVER_ADDR=[::1]:8848`）。后端机密只改环境变量 / 本地 `.env`，不要写回 yml。四服务 yml 占位符与 `OnePage-Server/env.example` 键一一对应；`.env` 进 gitignore，模板 `env.example` 可提交
- 注释写「做什么、为什么这样」和非常见注解/关键字；不要给每个 `if` 写废话。契约与表结构仍写工作空间 MD，不把事实只藏在注释里

## Git

| 项 | 约定（按团队改） |
| --- | --- |
| 主分支 | `main` |
| 功能分支 | `feat/<topic>`、`fix/<topic>` |
| 提交信息 | `feat:` / `fix:` / `docs:` 开头，说明为什么 |
| 评审 | 至少一人 Approve；契约变更必须 @FE @QA |

## 本地命令

```bash
npm install
npm run dev
npm run build
```

## 文档同步检查

- [ ] 写回 / Git 推送约定变了 → [工作空间写回约定](/engineering/process/workspace-writeback)
- [ ] 改了产品 UI / 接口 / 表却没改工作空间对应 MD → 同一轮补齐，见 `OnePage/AGENTS.md` 与 [Harness AGENTS](/space/harness/AGENTS) 回写表
- [ ] 业务 API 变了 → [接口契约](/engineering/process/contracts) · [API 明细](/engineering/process/api-endpoints)
- [ ] 表变了 → [数据库设计](/engineering/tech/database-schema) · [数据库脚本](/engineering/tech/sql) · Server `db/sql`
- [ ] 领域变了 → [领域设计](/engineering/tech/domain)
- [ ] 取舍定了 → [项目决策](/records/decisions)
- [ ] 前端结构变了 → [前端结构](/engineering/frontend)

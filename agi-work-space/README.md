<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="public/logo-dark.png" />
    <img src="public/logo-light.png" alt="ONEPAGE（一页）" width="360" />
  </picture>
</p>

<p align="center"><strong>知识写进仓库，人和 Agent 共用一份上下文</strong></p>

<p align="center">
  先读工作空间，再写代码。<br />
  若干年后重启项目，也不用从源码里考古。
</p>

<p align="center">
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-1c1c22?style=flat-square" /></a>
  <img alt="Vue 3" src="https://img.shields.io/badge/Vue-3-42b883?style=flat-square&logo=vuedotjs&logoColor=white" />
  <img alt="TypeScript" src="https://img.shields.io/badge/TypeScript-5-3178c6?style=flat-square&logo=typescript&logoColor=white" />
  <img alt="Vite" src="https://img.shields.io/badge/Vite-static-646cff?style=flat-square&logo=vite&logoColor=white" />
  <img alt="Markdown" src="https://img.shields.io/badge/facts-Markdown-000000?style=flat-square&logo=markdown&logoColor=white" />
  <img alt="No backend" src="https://img.shields.io/badge/backend-none-6b7280?style=flat-square" />
  <img alt="Agent ready" src="https://img.shields.io/badge/Agent-Cursor%20%7C%20Codex%20%7C%20Trae%20%7C%20Qoder-1c1c22?style=flat-square" />
  <a href="https://gitee.com/yangleduo7788/agi-work-space"><img alt="Gitee stars" src="https://gitee.com/yangleduo7788/agi-work-space/badge/star.svg?theme=dark" /></a>
  <a href="https://gitee.com/yangleduo7788/agi-work-space"><img alt="Gitee forks" src="https://gitee.com/yangleduo7788/agi-work-space/badge/fork.svg?theme=dark" /></a>
</p>

<p align="center">
  <a href="#为什么和以前不一样">为什么不一样</a> ·
  <a href="#架构">架构</a> ·
  <a href="#协作流程">流程</a> ·
  <a href="#角色怎么切开">角色</a> ·
  <a href="#30-秒上手">上手</a> ·
  <a href="#开源">开源</a>
</p>

---

## 这不是又一个文档站

AGI-WorkSpace 是 **yangleduo** 提出的一套协作模板：**一个真实项目，一份工作空间。**  
Vue 只是阅读和写回的壳。项目是什么、谁负责、接口怎么定、怎么验收、为什么当时那样决定——全部写在仓库的 Markdown 里，人和 AI 读的是同一棵树。

传统路径是：**打开代码 → 猜业务 → 再敢改。**  
这里反过来：**打开 Workspace → 范围、契约、角色已经写清 → Agent 按文档开发。** 代码要对齐文档，而不是文档去追代码。

适合 Cursor、Codex、Trae、Qoder 以及任何会读仓库文件的 Agent，也适合个人和团队一起把项目「养」完整。

> **一句话广告：** 把项目做成可执行的知识工作空间。聊天里的上下文会过期，仓库里的事实不会。

## 为什么和以前不一样

| 以前 | 现在 |
| --- | --- |
| 先通读代码，才能判断能改什么 | 先读 `src/content/`，结构、条例、功能一目了然 |
| Wiki、聊天、设计稿、仓库各说各话 | **一份 Git 历史**，人和 Agent 同源 |
| 换人或换 Agent，上下文要从头讲 | 打开这份 Workspace 就能接着做 |
| 若干年后接手 = 考古源码 | 文档在，项目就还在 |
| 给每篇说明再做一个页面 | 加知识 = 加一个 `.md`，不要加一个 Vue 页 |

没有自建后端、没有 CMS、没有账号系统。权限是七个角色勾选，写进 `src/config/workspace-roles.json`，Agent 看得见谁被允许动手。

## 架构

纯前端静态仓：壳负责读和写，事实只在 Markdown 与 Git。

```mermaid
flowchart TB
  subgraph shell ["Vue 壳 · 不承载业务事实"]
    UI["配置中心 / 文档预览 / 搜索"]
  end

  subgraph repo ["Git 仓库 · 单一事实来源"]
    MD["src/content/**/*.md"]
    Roles["src/config/workspace-roles.json"]
    H["根目录 AGENTS.md"]
    Brand["名称 · Logo · 口号"]
  end

  subgraph people ["人"]
    Team["团队 / 个人"]
  end

  subgraph agents ["Agent"]
    Bot["Cursor / Codex / Trae / Qoder"]
  end

  Team -->|"勾选角色、改文档"| UI
  UI -->|"写回磁盘"| MD
  UI --> Roles
  UI --> Brand
  Bot -->|"必读"| H
  Bot --> Roles
  Bot --> MD
  Bot -->|"按文档实现"| Impl["业务代码应对齐文档"]
  Impl -.->|"若干年后仍先读这里"| MD
```

## 协作流程

```mermaid
flowchart LR
  A["1. 克隆模板"] --> B["2. 换成当前项目"]
  B --> C["3. 配置中心勾选角色"]
  C --> D["4. 人按职责写 Markdown"]
  D --> E["5. Agent 读 AGENTS.md"]
  E --> F["6. 只改已勾选范围"]
  F --> G["7. 实现与文档一致"]
  G --> H["8. 多年后仍从 Workspace 接手"]
```

Harness 约束在根目录 [`AGENTS.md`](./AGENTS.md)（与网页里 Harness 那一份同步）。换项目只换文档和第 1 节表格，不改角色 ID，不为每篇文档拆路由。

## 角色怎么切开

```mermaid
flowchart TB
  W["一份 Workspace"]
  W --> S["开始阅读 · 任意已选角色"]
  W --> PM["PM · 产品 / 概述 / 目标"]
  W --> UIUE["UIUE · 设计"]
  W --> RD["RD · 架构 / 契约 / 领域"]
  W --> FE["FE · 前端结构"]
  W --> QA["QA · 质量"]
  W --> OP["OP · 运维"]
  W --> POM["POM · 交付 / 状态 / 决策"]
```

未勾选的角色：**可读、不能写**。Agent 碰到未勾选范围会停手，提示先去 Workspace 勾选。

## 30 秒上手

建议 Chrome / Edge。

```bash
npm install
npm run dev
```

打开后到 **空间配置 → 配置中心**：勾选角色、授权含 `src/content` 的项目根目录、按需更换 Logo。需要同步远程时再绑定代码空间。

```bash
npm run build
npm run preview
```

## 仓库里放什么

- 文件夹 = 侧栏分类（开始阅读 / 项目总览 / 产品设计 / 体验设计 / 研发设计 / 质量保障 / 交付运维 / 过程记录）
- `.md` = 一篇文档；`index.md` 是分类入口
- Frontmatter：`title`、`order`、`description`
- `src/content/space/` 是空间配置，不进「文档目录」树
- 角色勾选实时写入 `src/config/workspace-roles.json`
- Logo：`public/logo-light.png`、`public/logo-dark.png`

把模板占位换成**当前项目**。一份完整、可重启的知识，属于每一个用这份模板起的 Workspace 实例。

## 开源

MIT License · Copyright © 2026 **yangleduo**

欢迎 Star、Fork、提 Issue。你可以直接拿去套在下一个项目上：克隆 → 换名称和文档 → 让人和 Agent 围着同一份事实开发。

若这套「先 Workspace、后代码」的路子帮到你，把仓库丢给同事或下一任维护者——那就是这份模板存在的意义。

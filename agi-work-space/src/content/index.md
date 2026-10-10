---
title: 项目首页
description: ONEPAGE 工作空间入口与阅读路径
order: 0
---

> [!TIP]
> 按下面角色选一条阅读路径即可，不必一次读完。Agent 写代码前先读 [AGENTS.md](/space/harness/AGENTS) 与 [架构与 MVP 路线图](/engineering/tech/mvp-roadmap)。

## 这是什么

**ONEPAGE（一页）** 要做的是：PC 端生活方式社区（记录生活每一页），带 AI 和基础直播。  
**本仓库（agi-work-space）** 不是产品站本身，而是**项目笔记本**：产品、架构、接口、表结构都写在这里，人和 Agent 看同一份 Markdown。

产品代码在同级目录：

- `OnePage-web` — 前端（P0：shell 登录/注册/资料）
- `OnePage-Server` — 后端（P0：Gateway + auth + user + 上传，Nacos 发现）

## 当前状态（一眼看懂）

| 项 | 说明 |
| --- | --- |
| 产品名 | ONEPAGE（一页） |
| 一句话 | 记录生活每一页 · *Every Day, A New Page* |
| 阶段 | P0 进行中：本机 IDEA 起四个 Java 进程 + shell；发现流/发帖仍属 P1 |
| 本地预览 | 在本仓执行 `npm run dev`（建议 Chrome / Edge） |

## 按角色阅读

**产品经理 / 业务**

1. [项目介绍](/start/project-intro)  
2. [产品定义](/product/definition) → [用户与需求](/product/discovery/users) → [产品需求 PRD](/product/spec/prd)

**研发（前后端 / 架构）**

1. [架构与 MVP 路线图](/engineering/tech/mvp-roadmap)（总览，建议先读）  
2. [技术架构](/engineering/tech/architecture) → [架构图与时序图](/engineering/tech/diagrams) → [登录注册流程](/engineering/tech/auth-flows)  
3. [接口契约](/engineering/process/contracts) → 需要查字段时打开 [API 明细](/engineering/process/api-endpoints)、[数据库设计](/engineering/tech/database-schema)、[数据库脚本](/engineering/tech/sql)

**第一次用工作空间本身（改文档、勾角色）**

1. [WorkSpace 指南](/start/guide)  
2. [新范式](/start/paradigm)（可选，理解为什么用 Markdown 工作空间）

文档怎么写得更好读：[文档怎么写](/engineering/process/doc-style)。

## 各角色常用文档

| 角色 | 优先看 |
| --- | --- |
| PM | 定义 · 用户 · PRD · [项目概述](/project/charter/overview) |
| RD | 路线图 · 架构 · 契约 · [领域设计](/engineering/tech/domain) · 库表 |
| FE | [前端结构](/engineering/frontend) · API 明细 |
| QA | PRD 验收 · [测试计划](/quality/plan/testing)（待填） |
| UIUE | [体验](/design/system/experience) · PRD 信息架构 |
| OP / POM | [运维](/delivery/ops) · [项目状态](/project/governance/status) |

勾选角色与写权限见 [配置中心](/settings) 和 [项目角色](/project/governance/roles)。

## 使用约定（简短）

- 一篇 MD 一个主题；新内容优先改已有文档，避免重复造页。  
- 重要取舍记在 [项目决策](/records/decisions)，别只留在聊天里。  
- 改接口或表结构：先改文档，再写代码（见 [工程规范](/engineering/process/conventions)）。

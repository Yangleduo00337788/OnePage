---
title: 研发设计
description: 架构、契约、领域、规范与前端结构
order: 40
---

本组是实现侧的单一事实来源。**RD** 维护技术方案和工程过程；**FE** 维护前端结构。改栈或模块边界：**先改文档再改代码**。业务 API 在 `OnePage-Server`，不在本仓。

## 本组目录

| 目录 / 文档 | 写什么 | 维护 |
| --- | --- | --- |
| [技术方案](/engineering/tech) | 架构、路线图、[库表](/engineering/tech/database-schema)、[SQL 目录](/engineering/tech/sql) | RD |
| [工程过程](/engineering/process) | [接口契约](/engineering/process/contracts)、[工程规范](/engineering/process/conventions)、[研发协作](/engineering/process/collab)；Java 写法见 Harness [Java 开发规约](/space/harness/java-alibaba) | RD |
| [前端结构](/engineering/frontend) | 路由、状态、组件契约、无障碍 | FE |

产品要「做什么」以 [产品需求](/product/spec/prd) 为准；本组只回答「怎么做、约束是什么」。

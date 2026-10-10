---
title: 项目概述
description: ONEPAGE — 记录生活每一页
order: 1
---

PM 维护。用一段话让新成员或 Agent 在 2 分钟内理解这个项目。

**ONEPAGE（一页）** — 记录生活每一页。（Every Day, A New Page）

## 背景

- **为什么要做：** 把生活的每一页记录下来，并让项目知识集中在仓库里，方便人与 Agent 共用上下文。
- **现在怎么做、痛点是什么：** 没有按角色切开的单一工作空间，文档要么不能本地写回，要么必须会 Markdown。
- **相关系统 / 依赖方：** Git 仓库、设计工具、静态托管（若有）、CI。

## 范围

**做（产品 ONEPAGE）：**

- PC 生活方式社区：笔记、社交、私信/通知、AI、基础直播
- 后端微服务（`OnePage-Server`）、前端微应用（`OnePage-web`）
- 本工作空间：七角色项目文档与 Agent 可读契约

**做（本仓库 agi-work-space）：**

- 文档壳、角色写回、可选 Git 推送（见 [工作空间写回约定](/engineering/process/workspace-writeback)）

**不做（MVP）：**

- 礼物连麦带货、复杂推荐、完整运营后台
- 在文档壳内实现业务 API（API 在 OnePage-Server）

## 核心价值

1. 角色和目录绑定，职责可执行
2. 上下文在仓库里，人和 Agent 读同一份
3. 不会 Markdown 也能改文档

## 约束

- 时间：
- 合规 / 安全：授权目录只存在本机浏览器
- 技术或组织限制：写回依赖 Chrome / Edge

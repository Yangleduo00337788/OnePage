---
title: Harness 管理
description: 约束 Agent 使用本工作空间模板时的规则与入口
order: 92
---

Harness 是给 **Agent 工具**用的约束层：规定可以改哪些文件、必须先读什么、禁止做什么。换项目时优先改 [AGENTS.md](/space/harness/AGENTS)，不要改站点结构。

## 当前项目入口

- [AGENTS.md](/space/harness/AGENTS) — 主约束文件。保存后同步到 `agi-work-space/AGENTS.md`。Cursor 打开父目录 `OnePage/` 时读 **`OnePage/AGENTS.md`**（回写表、Java 必读）。约束只进 Git 里的这些 MD，**不要**写 `.cursor/rules`（会被 ignore，换机丢失）。
- [Java 开发规约](/space/harness/java-alibaba) — 改 `OnePage-Server` 必读。章节对齐阿里 Java 手册，正文为本项目约定（官方手册全文受版权保护，不进本仓）。
- `src/config/workspace-roles.json` — 当前勾选角色。配置中心勾选会立刻写入，Agent 动手前必须读。

## 使用方式

1. 在侧栏打开「空间配置 → Harness 管理」。
2. 编辑 AGENTS.md：补上项目名称、仓库、禁止目录、必须走的检查。
3. 授权本地工作目录后保存，`agi-work-space/AGENTS.md` 与本页 Harness 正文一致。回写表若有改，同步父目录 `OnePage/AGENTS.md`。

## 还可以放什么

后续还可在本目录增加运行手册、评测集、工具白名单等，仍从 Harness 管理进入，不进入「文档目录」树。Java 规约已放 [java-alibaba](/space/harness/java-alibaba)。

---
title: Harness 管理
description: 约束 Agent 使用本工作空间模板时的规则与入口
order: 92
---

Harness 是给 **Agent 工具**用的约束层：规定可以改哪些文件、必须先读什么、禁止做什么。换项目时优先改 [AGENTS.md](/space/harness/AGENTS)，不要改站点结构。

## 当前项目入口

- [AGENTS.md](/space/harness/AGENTS) — 主约束文件。保存后会同步到仓库根目录 `AGENTS.md`，供 Cursor / 其它 Agent 读取。
- `src/config/workspace-roles.json` — 当前勾选角色。配置中心勾选会立刻写入，Agent 动手前必须读。

## 使用方式

1. 在侧栏打开「空间配置 → Harness 管理」。
2. 编辑 AGENTS.md：补上项目名称、仓库、禁止目录、必须走的检查。
3. 授权本地工作目录后保存，根目录与 `src/content/space/harness/AGENTS.md` 保持同一套正文。

## 还可以放什么

后续可在本目录增加运行手册、评测集、工具白名单等，仍从 Harness 管理进入，不进入「文档目录」树。

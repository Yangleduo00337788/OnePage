---
title: 项目引用
description: 本工作空间如何引用当前项目、目录、角色与远程仓库
order: 91
---

给换项目、新成员和 Agent 看：这份工作空间引用的是哪一套项目事实，改哪里、不要改哪里。

## 这份空间引用什么

AGI-WorkSpace 是**项目工作空间模板**，不是独立业务系统。当前仓库里的 Markdown、角色表和远程绑定，共同描述「正在做的那个项目」。

换项目时，应替换引用内容，而不是新建一套 Vue 页面。

## 应该改的引用

| 位置 | 写什么 |
| --- | --- |
| [项目概述](/project/charter/overview) | 背景、范围、非目标 |
| [项目角色](/project/governance/roles) | 七个角色的对接人 |
| [配置中心](/settings) | 本机目录、当前角色（`src/config/workspace-roles.json`）、工作空间标识、代码空间、推送文案 |
| [Harness / AGENTS.md](/space/harness/AGENTS) | Agent 允许做什么、禁止做什么 |

## 目录约定

- 业务文档只放 `src/content/` 下的项目总览 / 产品设计 / 体验设计 / 研发设计 / 质量保障 / 交付运维 / 过程记录。
- 空间配置文档放 `src/content/space/`，**不出现在「文档目录」导航树**，只从「空间配置」进入。
- 授权工作目录后，侧栏以磁盘 Markdown 为准；有角色权限可新建 / 删除文档，图片放在文档 `assets/`。
- 访问令牌只写本机 `.agi-workspace.local.json`，已加入 `.gitignore`，不要写进引用文档。

## 远程仓库

绑定的 GitHub / Gitee / GitLab 仓库就是项目源码与文档的单一事实来源。网页里保存 Markdown 会写回 `src/content`；「提交并推送」按根目录 `.gitignore` 提交整个仓库。

## Agent 怎么用这份引用

1. 先读本页和 [项目概述](/project/charter/overview)，再改具体文档。
2. 写权限与可开发范围以 [配置中心](/settings) 勾选为准，文件是 `src/config/workspace-roles.json`。未勾选的角色禁止 Agent 修改。
3. 工具调用与仓库改动必须遵守 [AGENTS.md](/space/harness/AGENTS)。

---
title: 工作空间写回约定
description: agi-work-space 文档壳的浏览器写回与 Git 推送（非业务 API）
order: 4
---

RD / FE 维护。仅描述 **本仓库** `agi-work-space` 的纯前端写回；业务 REST 见 [接口契约](/engineering/process/contracts)。

## 工程形态

| 项 | 约定 |
| --- | --- |
| 运行 | Vite 开发 / 静态 `dist/`，History 路由回退 `index.html` |
| 业务 API | **不在本仓实现**；见 `OnePage-Server` |
| 数据 | Markdown 在 `src/content/`；角色在 `src/config/workspace-roles.json`；主题等在 localStorage；目录句柄与草稿在 IndexedDB |
| 鉴权 | 配置中心勾选七个角色并写回 JSON，不是 ONEPAGE 用户登录 |

## 浏览器写回

| 能力 | 约定 |
| --- | --- |
| 前提 | Chrome / Edge；已授权含 `src/content` 的项目根目录 |
| 保存文档 | 有角色权限才写 `src/content/**/*.md`；frontmatter 写入 `updated`（ISO 时间） |
| 重命名 / 移动 | 有写权限且非首页；目录页连同子文件一起搬 |
| 草稿 | 编辑中自动写入 IndexedDB；离开页前提示未保存 |
| 图片 | 写入该文档目录 `assets/` |
| 未授权 | 可预览，不能落盘 |

## Git 平台（可选推送）

令牌在 `.agi-workspace.local.json`，禁止进仓库。本地 dev 用 Vite 代理 `/__git/*`；本阶段不做应用内 pull/合并。

## 角色与可写范围

以 [项目角色](/project/governance/roles) 和 `src/lib/roles.ts` 为准。

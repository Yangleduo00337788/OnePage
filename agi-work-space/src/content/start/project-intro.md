---
title: 项目介绍文档
description: ONEPAGE 是什么、现在做到哪
order: 1
---

说明 ONEPAGE 在做什么、文档放在哪、产品代码是否已开工。改功能范围见 [产品定义](/product/definition) 与 [PRD](/product/spec/prd)。

## 一句话

**ONEPAGE（一页）** — 在 PC 上记录和发现生活每一页。文档在本工作空间；产品程序在同级 `OnePage-web` / `OnePage-Server`（**P0 已开工**：能登录、建资料、拿上传凭证）。

## 现在处于什么阶段

| 问题 | 答案 |
| --- | --- |
| 产品是什么 | 生活方式社区 + AI + 基础直播（详见 PRD） |
| 文档 | 需求、架构、接口、库表已写在 `src/content` |
| 代码 | P0 已落地；yml 占位符 + 进程环境变量启动（本地 IDEA 注入 `.env`），不用 Docker |
| 本网站 | 你现在用的是「项目笔记本」，不是给用户上的线站 |

## 新人建议阅读顺序

1. [项目首页](/) — 按角色找链接  
2. [产品定义](/product/definition) → [PRD](/product/spec/prd)  
3. [架构与 MVP 路线图](/engineering/tech/mvp-roadmap)（研发）  
4. [WorkSpace 指南](/start/guide) — 若要改文档、勾角色、写回本地

## 工作空间还能干什么

和 AGI 模版一样：七个角色、Markdown 文档、配置中心勾选权限、可选 Git 推送。  
这和 ONEPAGE 用户注册登录**无关**；那是产品站以后的事。

更细的边界见 [项目概述](/project/charter/overview)。

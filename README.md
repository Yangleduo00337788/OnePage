<div style="text-align: center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="OnePage-web/apps/shell/public/logo-dark.png" />
  <img src="OnePage-web/apps/shell/public/logo-light.png" alt="ONEPAGE（一页）" width="280" style="display: block; margin: 0 auto" />
</picture>

<h1 style="text-align: center">ONEPAGE（一页）</h1>

<p style="text-align: center">记录生活每一页。<br />Every Day, A New Page</p>

</div>

<p align="center">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-1c1c22?style=flat-square" />
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk&logoColor=white" />
  <img alt="Spring Boot 3.4.5" src="https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
  <img alt="Spring Cloud 2024.0.1" src="https://img.shields.io/badge/Spring%20Cloud-2024.0.1-6DB33F?style=flat-square&logo=spring&logoColor=white" />
  <img alt="Vue 3.5" src="https://img.shields.io/badge/Vue-3.5-42b883?style=flat-square&logo=vuedotjs&logoColor=white" />
  <img alt="TypeScript 5.8" src="https://img.shields.io/badge/TypeScript-5.8-3178c6?style=flat-square&logo=typescript&logoColor=white" />
  <img alt="Vite 6" src="https://img.shields.io/badge/Vite-6-646cff?style=flat-square&logo=vite&logoColor=white" />
  <img alt="pnpm 9" src="https://img.shields.io/badge/pnpm-9-f69220?style=flat-square&logo=pnpm&logoColor=white" />
</p>

<p align="center">
  <img alt="Tailwind CSS 4" src="https://img.shields.io/badge/Tailwind%20CSS-4-06b6d4?style=flat-square&logo=tailwindcss&logoColor=white" />
  <img alt="Pinia 3" src="https://img.shields.io/badge/Pinia-3-ffd859?style=flat-square&logo=pinia&logoColor=black" />
  <img alt="MyBatis-Plus 3.5.9" src="https://img.shields.io/badge/MyBatis--Plus-3.5.9-000000?style=flat-square" />
  <img alt="MySQL 8.4" src="https://img.shields.io/badge/MySQL-8.4-4479a1?style=flat-square&logo=mysql&logoColor=white" />
  <img alt="Redis 8.0" src="https://img.shields.io/badge/Redis-8.0-ff4438?style=flat-square&logo=redis&logoColor=white" />
  <img alt="Nacos 3.2.4" src="https://img.shields.io/badge/Nacos-3.2.4-000000?style=flat-square" />
  <img alt="Elasticsearch 9.5.4" src="https://img.shields.io/badge/Elasticsearch-9.5.4-005571?style=flat-square&logo=elasticsearch&logoColor=white" />
  <img alt="MinIO" src="https://img.shields.io/badge/MinIO-2025--02-c72e49?style=flat-square&logo=minio&logoColor=white" />
</p>

<p align="center">
  <a href="#简介">简介</a> ·
  <a href="#特性">特性</a> ·
  <a href="#技术栈">技术栈</a> ·
  <a href="#仓库结构">仓库结构</a> ·
  <a href="#快速开始">快速开始</a> ·
  <a href="#参与贡献">参与贡献</a> ·
  <a href="#开源协议">开源协议</a>
</p>

---

## 简介

ONEPAGE 是面向 PC 的生活方式社区：用笔记记下生活的每一页，并带上关注、通知、私信、搜索、AI 和基础直播。

本目录是产品与知识库的总入口。浏览器只访问网关；登录态放在 Redis Session 里；服务之间需要立刻得到结果时走 Feign。架构、接口、表和决策以 `agi-work-space/src/content` 为准。

## 🎉 特性

- 笔记流支持图文和视频，关注、赞藏评、分享在同一条主链路上
- 通知和私信分开；私信支持文本、图片，以及笔记卡片、直播卡片
- 全局搜索覆盖公开笔记、用户、话题和正在直播
- AI 负责润色、问答和画面机审；基础直播支持推流、列表和直播间文字
- 社区、创作者、运营后台分成三个前端应用，运营不挂在社区导航上
- 项目事实写在仓库 Markdown 里，人和 Agent 读同一份

## 🛠 技术栈

版本以 `OnePage-Server/pom.xml` 与 `OnePage-web` 的 `package.json` 为准。

| 层 | 徽标 | 说明 |
| --- | --- | --- |
| 语言 | ![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk&logoColor=white) | 业务进程 Java 21 |
| 后端 | ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2024.0.1-6DB33F?style=flat-square&logo=spring&logoColor=white) | Gateway + 九个业务服务；Spring Cloud Alibaba `2023.0.3.2` |
| 数据访问 | ![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.9-000000?style=flat-square) | 一服务一库；DDL 用手工脚本 |
| 前端 | ![Vue](https://img.shields.io/badge/Vue-3.5-42b883?style=flat-square&logo=vuedotjs&logoColor=white) ![TypeScript](https://img.shields.io/badge/TypeScript-5.8-3178c6?style=flat-square&logo=typescript&logoColor=white) ![Vite](https://img.shields.io/badge/Vite-6-646cff?style=flat-square&logo=vite&logoColor=white) | pnpm 单仓多应用 |
| 界面 | ![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-4-06b6d4?style=flat-square&logo=tailwindcss&logoColor=white) ![Pinia](https://img.shields.io/badge/Pinia-3-ffd859?style=flat-square&logo=pinia&logoColor=black) | Vue Router 4；请求带 Cookie |
| 注册与缓存 | ![Nacos](https://img.shields.io/badge/Nacos-3.2.4-000000?style=flat-square) ![Redis](https://img.shields.io/badge/Redis-8.0-ff4438?style=flat-square&logo=redis&logoColor=white) | 服务发现；Session、验证码、私信路由 |
| 存储与搜索 | ![MySQL](https://img.shields.io/badge/MySQL-8.4-4479a1?style=flat-square&logo=mysql&logoColor=white) ![Elasticsearch](https://img.shields.io/badge/Elasticsearch-9.5.4-005571?style=flat-square&logo=elasticsearch&logoColor=white) ![MinIO](https://img.shields.io/badge/MinIO-2025--02-c72e49?style=flat-square&logo=minio&logoColor=white) | 业务库、搜索索引、媒体直传 |
| 文档壳 | ![Vue](https://img.shields.io/badge/Vue-3-42b883?style=flat-square&logo=vuedotjs&logoColor=white) ![Markdown](https://img.shields.io/badge/Markdown-facts-000000?style=flat-square&logo=markdown&logoColor=white) | `agi-work-space` 只渲染知识空间 |

RocketMQ `5.5.0` 已选定版本。发帖仍写 MySQL Outbox，当前不消费消息。

## 📦 仓库结构

```text
OnePage/
├── OnePage-web/        产品前端：社区、创作者、运营
├── OnePage-Server/     网关与九个 Spring Boot 服务
├── OnePage-demo/       静态演示，不启后端
├── agi-work-space/     知识空间与文档壳
└── loadtest/           压测脚本
```

| 目录 | 做什么 | 本地入口 |
| --- | --- | --- |
| `OnePage-web/apps/shell` | 社区 | http://localhost:5174 |
| `OnePage-web/apps/app-creator` | 创作者 | http://localhost:5175 |
| `OnePage-web/apps/app-ops` | 运营后台 | http://localhost:5176 |
| `OnePage-Server/onepage-gateway` | 对外 API | http://localhost:8080 |
| `agi-work-space` | 项目文档 | 见该目录 `npm run dev` |

业务服务端口：auth `8081`、user `8082`、content `8083`、notification `8084`、message `8085`、moderation `8086`、search `8087`、ai `8088`、live `8089`。

## 🚀 快速开始

环境：JDK 21、Maven、Node.js、pnpm 9、MySQL 8.4、Redis 8、Nacos 3.2.4。搜索和上传再准备 Elasticsearch 9.5.4 与 MinIO。

### 后端

```bash
cd OnePage-Server
# 复制 env.example 为 .env，按本机填写。程序不读该文件，由 IDEA 注入环境变量
```

在 IDEA 中为十个 Maven 模块各建一份运行配置，Environment files 指向 `OnePage-Server/.env`。模块名与启动说明见 [OnePage-Server/README.md](OnePage-Server/README.md)。`application.yml` 只写 `${变量}`。

### 前端

```bash
cd OnePage-web
pnpm install
pnpm dev          # 社区 5174 + 创作者 5175
pnpm dev:ops      # 运营后台 5176
```

`/api` 的代理目标来自 `VITE_DEV_PROXY_TARGET`，默认打到网关 `8080`。细节见 [OnePage-web/README.md](OnePage-web/README.md)。

### 文档壳

```bash
cd agi-work-space
npm install
npm run dev
```

## 🔨 请求怎么走

1. 浏览和发帖：浏览器 → 前端 → Gateway → 业务服务 → MySQL 或 Elasticsearch。
2. 登录：Cookie + Redis Session。内网身份只认网关写下的 `X-User-Id`。
3. 图片和视频：先拿上传凭证，浏览器直传 MinIO，再绑到笔记。

服务边界、接口和表结构写在 `agi-work-space/src/content/engineering/`。

## 🤝 参与贡献

1. 从当前主干拉出功能分支
2. 按仓库约定改代码，并在同一轮把结论写回 `agi-work-space/src/content`
3. 提交说明使用 `type(scope): 变更主要信息`
4. 发起 Pull Request

`type` 取 `feat`、`fix`、`docs`，`scope` 必填，冒号为英文冒号并跟一个空格。示例：`feat(im): 私信改为长连接推送并能发图片`。

Java 写法见 `agi-work-space/src/content/space/harness/java-alibaba.md`。不要把密钥、`.env` 和证书写进文档或提交记录。

## 📄 开源协议

知识空间 `agi-work-space` 遵循 [MIT 协议](agi-work-space/LICENSE)。

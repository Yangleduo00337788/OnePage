---
title: 压测报告
description: 按业界口径整理的通过轮次
order: 2
---

每份报告按业界口径写：QPS（吞吐量）、TPS（每秒事务数）、RT（响应时间）的 P50 / P90 / P95 / Max（最大值）、Error Rate（错误率）、CPU（处理器）和 Memory（内存）。QPS（吞吐量）是全程平均，含 Ramp-up（爬升）。k6 汇总没有 P99（99 分位）。

| 报告 | Concurrency（并发数） | QPS（吞吐量） | Error Rate（错误率） |
| --- | --- | --- | --- |
| [100 并发读路径](/quality/loadtest/reports/r100) | 100 | 每个接口 93 | 0 |
| [500 并发注册与读路径](/quality/loadtest/reports/r500) | 500 | 每个接口 34 | 0 |
| [已有账号登录](/quality/loadtest/reports/r-login) | 200 | 登录 124 | 0 |
| [500 并发搜索](/quality/loadtest/reports/r-search) | 500 | 每个 Tab 631 | 0 |
| [500 并发发笔记](/quality/loadtest/reports/r-post) | 500 | 凭证和发笔记各 373 | 0 |
| [50 并发互动](/quality/loadtest/reports/r-react) | 50 | 点赞、收藏、评论各 219 | 0 |
| [50 并发私信](/quality/loadtest/reports/r-im) | 50 | 建会话和发消息各 375 | 0 |
| [50 并发直播](/quality/loadtest/reports/r-live) | 50 | 列表、进房、聊天各 692 | 0 |

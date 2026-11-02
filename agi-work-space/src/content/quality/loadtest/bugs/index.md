---
title: 压测缺陷
description: 压测发现的容量与并发缺陷
order: 1
---

每条缺陷单独一页，写清当次场景、数字、日志和调用链。关闭时在原页补验证轮次，不要只改状态词。

| ID | 问题 | 发现于 | 级别 | 状态 |
| --- | --- | --- | --- | --- |
| [LT-001](/quality/loadtest/bugs/lt-001) | 内容服务按卡片 Feign 拉作者，Windows 临时端口耗尽 | 2026-10-30 `read-ramp.js` | P1 | 已验收 |
| [LT-002](/quality/loadtest/bugs/lt-002) | 网关按客户端 IP、按路由每秒放行约 50 次，单一地址的阶梯加压在进服务前被拒绝 | 2026-10-31 `read-ramp.js` | P1 | 已验收 |
| [LT-003](/quality/loadtest/bugs/lt-003) | 发现流按卡片查封面和计数，默认约 10 条数据库连接被挤满 | 2026-11-01 `mix-ramp.js` | P1 | 已改读许可，复跑未过 |

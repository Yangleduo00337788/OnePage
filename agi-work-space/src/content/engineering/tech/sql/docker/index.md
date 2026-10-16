---
title: docker
description: 对应 OnePage-Server/docker/mysql/init/
order: 4
---

Docker 进容器时跑的初始化，不是第三套表结构。表以各服务 `db/sql/` 为准。

- [00-databases.sql](/engineering/tech/sql/docker/00-databases)
- [01-tables.sql](/engineering/tech/sql/docker/01-tables)

ES 不进 MySQL 初始化。本机把 `OnePage-Server/docker/elasticsearch-compose.snippet.yml` 贴进 WSL `~/docker/compose.yml`。

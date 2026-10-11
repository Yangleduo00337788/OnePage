---
title: 003_category.sql
description: onepage-content-service/db/sql/003_category.sql
order: 4
---

对应 `OnePage-Server/onepage-content-service/db/sql/003_category.sql`。给已有 `post` 加 `category`。新库若直接跑带 category 的 [002_p1.sql](/engineering/tech/sql/onepage-content-service/002_p1)，本脚本会因列已存在而失败，按 CHANGELOG 只在缺列的库执行。缺列时推荐流 500。

## SQL

原文：

```sql
ALTER TABLE post
  ADD COLUMN category VARCHAR(16) NOT NULL DEFAULT '' COMMENT '首页分类' AFTER post_type,
  ADD KEY idx_category_published (category, status, published_at, id);
```

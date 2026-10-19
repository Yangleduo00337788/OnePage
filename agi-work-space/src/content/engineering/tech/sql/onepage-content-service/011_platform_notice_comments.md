---
title: 011_platform_notice_comments.sql
description: onepage-content-service/db/sql/011_platform_notice_comments.sql
order: 12
---

对应 `OnePage-Server/onepage-content-service/db/sql/011_platform_notice_comments.sql`。只改 `platform_notice` 的表/列 COMMENT。PowerShell 管道执行 `009` 时注释会变成「鍒涗綔…」这类乱码，数据行（一页公约）已在 utf8mb4 下改正。

## SQL

原文：

```sql
USE onepage_content;

ALTER TABLE platform_notice COMMENT='创作者平台公告';
ALTER TABLE platform_notice
  MODIFY id BIGINT NOT NULL COMMENT '公告 ID',
  MODIFY title VARCHAR(64) NOT NULL COMMENT '标题',
  MODIFY body VARCHAR(500) NOT NULL DEFAULT '' COMMENT '正文',
  MODIFY sort_order INT NOT NULL DEFAULT 0 COMMENT '列表顺序，小的在前',
  MODIFY enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 展示 0 停用',
  MODIFY created_at DATETIME(3) NOT NULL COMMENT '创建时间';
```

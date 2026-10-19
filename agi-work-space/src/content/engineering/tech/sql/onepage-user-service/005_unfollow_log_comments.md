---
title: 005_unfollow_log_comments.sql
description: onepage-user-service/db/sql/005_unfollow_log_comments.sql
order: 5
---

对应 `OnePage-Server/onepage-user-service/db/sql/005_unfollow_log_comments.sql`。只改 `user_unfollow_log` 的表/列 COMMENT。PowerShell 管道执行 `003` 时注释会乱码。

## SQL

原文：

```sql
USE onepage_user;

ALTER TABLE user_unfollow_log COMMENT='取关日志';
ALTER TABLE user_unfollow_log
  MODIFY id BIGINT NOT NULL COMMENT '记录 ID',
  MODIFY follower_id BIGINT NOT NULL COMMENT '取关人',
  MODIFY followee_id BIGINT NOT NULL COMMENT '被取关人',
  MODIFY followed_at DATETIME(3) NOT NULL COMMENT '当时关注边的创建时间',
  MODIFY unfollowed_at DATETIME(3) NOT NULL COMMENT '取关时间';
```

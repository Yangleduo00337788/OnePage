---
title: 003_community_policy.sql
description: onepage-moderation-service/db/sql/003_community_policy.sql
order: 3
---

对应 `OnePage-Server/onepage-moderation-service/db/sql/003_community_policy.sql`。已有 `onepage_moderation` 必须执行，否则运营「社区规则」500。可重复执行。

## 表

| 表 | 作用 |
| --- | --- |
| community_policy | 单行：违禁词、昵称/签名长度、图片/视频 MB 上限。运营 `GET/POST /ops/policy` |

## SQL

原文：

```sql
-- 社区规则：违禁词与媒体上限。运营后台可改。已有库必须执行。可重复执行。
USE onepage_moderation;

CREATE TABLE IF NOT EXISTS community_policy (
  id TINYINT NOT NULL COMMENT '固定 1，单行配置',
  banned_words TEXT NOT NULL COMMENT '违禁词，一行一个',
  nickname_max INT NOT NULL COMMENT '昵称最长字数',
  bio_max INT NOT NULL COMMENT '签名最长字数',
  image_max_mb INT NOT NULL COMMENT '图片上限 MB',
  video_max_mb INT NOT NULL COMMENT '视频上限 MB',
  updated_at DATETIME(3) NOT NULL COMMENT '最后保存时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区规则（违禁词与上传上限）';

INSERT INTO community_policy (
  id, banned_words, nickname_max, bio_max, image_max_mb, video_max_mb, updated_at
) VALUES (
  1,
  '代孕\n办证\n发票\n枪支\n毒品\n约炮\n色情\n赌博\n加微信\n加vx\n加v信\n澳门赌场\n六合彩',
  24,
  160,
  10,
  200,
  NOW(3)
) ON DUPLICATE KEY UPDATE id = id;
```

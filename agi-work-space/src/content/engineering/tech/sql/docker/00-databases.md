---
title: 00-databases.sql
description: docker/mysql/init/00-databases.sql
order: 1
---

对应 `OnePage-Server/docker/mysql/init/00-databases.sql`。Docker 初始化建已落地的库（含通知、私信库）。本地 IDEA 在本机 MySQL 执行同样语句。

## SQL

原文：

```sql
CREATE DATABASE IF NOT EXISTS onepage_auth DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_content DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_notification DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_message DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

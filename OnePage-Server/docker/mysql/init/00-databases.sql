-- 可选 Docker 初始化：建已落地的库。本地 IDEA 开发请在本机 MySQL 手工执行同样语句。
-- 规划中尚未建的库（social / message 等）见工作空间 database-schema.md，不要自编未入库的 sql 文件名。

CREATE DATABASE IF NOT EXISTS onepage_auth DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_content DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_notification DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_message DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

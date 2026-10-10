-- 可选 Docker 初始化：建 P0 三个库。本地 IDEA 开发请在本机 MySQL 手工执行同样语句。
-- 完整库清单（含 social / message 等）见 agi-work-space/src/content/engineering/tech/sql/00-create-databases.sql

CREATE DATABASE IF NOT EXISTS onepage_auth DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_content DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

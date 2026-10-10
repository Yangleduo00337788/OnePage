-- =============================================================================
-- 建库（本地 IDEA：在本机 MySQL 执行一次）
-- 一个微服务一个库；不要跨库外键。P0 至少建 auth / user / content。
-- 字段字典：[数据库设计](../database-schema.md)
-- =============================================================================

CREATE DATABASE IF NOT EXISTS onepage_auth DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_content DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_social DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_message DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_notification DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_moderation DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS onepage_live DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

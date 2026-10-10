-- =============================================================================
-- 库：onepage_auth
-- 落地：P0 已在 OnePage-Server/onepage-auth-service/db/sql/001_init.sql
-- 执行：USE onepage_auth;
-- =============================================================================

CREATE TABLE IF NOT EXISTS auth_account (
  id BIGINT PRIMARY KEY COMMENT '全局 user_id，注册时生成',
  phone VARCHAR(20) NULL COMMENT '手机号，国内 11 位或 E.164',
  email VARCHAR(128) NULL COMMENT '邮箱',
  password_hash VARCHAR(255) NULL COMMENT 'BCrypt；纯 OAuth 可空',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 正常 1 冻结',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_phone (phone),
  UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录账号';

CREATE TABLE IF NOT EXISTS auth_oauth_binding (
  id BIGINT PRIMARY KEY COMMENT '绑定记录 ID',
  user_id BIGINT NOT NULL COMMENT '对应 auth_account.id',
  provider VARCHAR(16) NOT NULL COMMENT 'github / google',
  provider_user_id VARCHAR(64) NOT NULL COMMENT '第三方侧用户唯一 ID',
  created_at DATETIME(3) NOT NULL COMMENT '绑定时间',
  UNIQUE KEY uk_provider_user (provider, provider_user_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth 绑定';

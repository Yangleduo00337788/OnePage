-- 可选 Docker 初始化：仅 P0 表。字段注释与各服务 db/sql/001_init.sql 保持一致。
-- 全量表结构以工作空间 sql/ 目录为准。

USE onepage_auth;

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
  user_id BIGINT NOT NULL COMMENT '对应 auth_account.id / 用户资料 user_id',
  provider VARCHAR(16) NOT NULL COMMENT 'github / google',
  provider_user_id VARCHAR(64) NOT NULL COMMENT '第三方侧用户唯一 ID',
  created_at DATETIME(3) NOT NULL COMMENT '绑定时间',
  UNIQUE KEY uk_provider_user (provider, provider_user_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth 绑定';

USE onepage_user;

CREATE TABLE IF NOT EXISTS user_profile (
  user_id BIGINT PRIMARY KEY COMMENT '与 auth_account.id 一致',
  page_id VARCHAR(16) NOT NULL COMMENT '一页号，对外展示，创建后不可改',
  nickname VARCHAR(64) NOT NULL COMMENT '昵称',
  avatar_object_key VARCHAR(512) NULL COMMENT '头像 MinIO object key',
  cover_object_key VARCHAR(512) NULL COMMENT '主页封面 MinIO object key',
  bio VARCHAR(500) NULL COMMENT '签名',
  gender TINYINT NOT NULL DEFAULT 0 COMMENT '0 未知 1 男 2 女',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_page_id (page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资料';

CREATE TABLE IF NOT EXISTS user_stat (
  user_id BIGINT PRIMARY KEY COMMENT '与 user_profile.user_id 一致',
  following_count INT NOT NULL DEFAULT 0 COMMENT '关注数',
  follower_count INT NOT NULL DEFAULT 0 COMMENT '粉丝数',
  like_received_count INT NOT NULL DEFAULT 0 COMMENT '笔记获赞数',
  favorite_received_count INT NOT NULL DEFAULT 0 COMMENT '笔记被藏数',
  updated_at DATETIME(3) NOT NULL COMMENT '最后累加时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户计数';

USE onepage_content;

CREATE TABLE IF NOT EXISTS media_asset (
  id BIGINT PRIMARY KEY COMMENT '对外 mediaId',
  owner_id BIGINT NOT NULL COMMENT '上传者 user_id',
  bucket VARCHAR(64) NOT NULL COMMENT 'MinIO 桶名，默认 onepage-media',
  object_key VARCHAR(512) NOT NULL COMMENT '对象键',
  mime_type VARCHAR(128) NOT NULL COMMENT '如 image/jpeg',
  size_bytes BIGINT NOT NULL COMMENT '字节数',
  duration_sec INT NULL COMMENT '视频时长秒；图片空',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 上传中 1 就绪 2 失败',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  KEY idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='媒体对象';

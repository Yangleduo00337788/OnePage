-- =============================================================================
-- 库：onepage_user（先 USE onepage_user 再执行）
-- 阶段：P0 仅资料 + 计数。关注表 user_follow、浏览历史 browse_history 见工作空间完整脚本，P2 再建。
-- =============================================================================

-- 公开资料。user_id 与 auth_account.id 相同，本库不建跨库外键。
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

-- 计数冗余。关注/赞藏真实关系在别的表，本表由 MQ 或同步累加，避免每次 COUNT。
CREATE TABLE IF NOT EXISTS user_stat (
  user_id BIGINT PRIMARY KEY COMMENT '与 user_profile.user_id 一致',
  following_count INT NOT NULL DEFAULT 0 COMMENT '关注数',
  follower_count INT NOT NULL DEFAULT 0 COMMENT '粉丝数',
  like_received_count INT NOT NULL DEFAULT 0 COMMENT '笔记获赞数',
  favorite_received_count INT NOT NULL DEFAULT 0 COMMENT '笔记被藏数',
  updated_at DATETIME(3) NOT NULL COMMENT '最后累加时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户计数';

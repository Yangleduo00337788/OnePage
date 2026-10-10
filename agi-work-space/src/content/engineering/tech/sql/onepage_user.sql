-- =============================================================================
-- 库：onepage_user
-- 落地：P0 仅 user_profile、user_stat（见 Server 001_init.sql）
-- 待建：user_follow、browse_history（P2）
-- 执行：USE onepage_user;
-- =============================================================================

CREATE TABLE IF NOT EXISTS user_profile (
  user_id BIGINT PRIMARY KEY COMMENT '与 auth_account.id 一致，不建跨库外键',
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

-- P2：关注关系。发 MQ 给 notification。
CREATE TABLE IF NOT EXISTS user_follow (
  id BIGINT PRIMARY KEY COMMENT '关注记录 ID',
  follower_id BIGINT NOT NULL COMMENT '关注人 user_id',
  followee_id BIGINT NOT NULL COMMENT '被关注人 user_id',
  created_at DATETIME(3) NOT NULL COMMENT '关注时间',
  UNIQUE KEY uk_follow (follower_id, followee_id),
  KEY idx_followee (followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关注';

-- P2：浏览历史，个人页「足迹」。
CREATE TABLE IF NOT EXISTS browse_history (
  id BIGINT PRIMARY KEY COMMENT '记录 ID',
  user_id BIGINT NOT NULL COMMENT '浏览人',
  post_id BIGINT NOT NULL COMMENT '笔记 ID（content 库，仅存 ID）',
  viewed_at DATETIME(3) NOT NULL COMMENT '浏览时间',
  KEY idx_user_viewed (user_id, viewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览历史';

-- 关注事件 Outbox（P2 启用，表结构各服务相同）
CREATE TABLE IF NOT EXISTS outbox_event (
  id BIGINT PRIMARY KEY COMMENT '事件 ID，作 MQ 幂等键',
  aggregate_type VARCHAR(32) NOT NULL COMMENT '如 follow',
  aggregate_id VARCHAR(64) NOT NULL COMMENT '聚合根 ID',
  event_type VARCHAR(64) NOT NULL COMMENT '如 user.followed',
  payload_json JSON NOT NULL COMMENT '事件正文',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待发送 1 已发送 2 失败',
  created_at DATETIME(3) NOT NULL COMMENT '写入时间',
  sent_at DATETIME(3) NULL COMMENT '发送成功时间',
  KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox';

-- =============================================================================
-- 库：onepage_social（目标形态）
-- P1 实际建在 onepage_content（见 onepage_content.sql / 002_p1.sql），本文件留给拆独立社交服务时用。
-- 执行：USE onepage_social;
-- =============================================================================

CREATE TABLE IF NOT EXISTS post_like (
  user_id BIGINT NOT NULL COMMENT '点赞人',
  post_id BIGINT NOT NULL COMMENT '笔记 ID',
  created_at DATETIME(3) NOT NULL COMMENT '点赞时间',
  PRIMARY KEY (user_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赞笔记';

CREATE TABLE IF NOT EXISTS post_favorite (
  user_id BIGINT NOT NULL COMMENT '收藏人',
  post_id BIGINT NOT NULL COMMENT '笔记 ID',
  created_at DATETIME(3) NOT NULL COMMENT '收藏时间',
  PRIMARY KEY (user_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='藏笔记';

CREATE TABLE IF NOT EXISTS comment (
  id BIGINT PRIMARY KEY COMMENT '评论 ID',
  post_id BIGINT NOT NULL COMMENT '笔记 ID',
  user_id BIGINT NOT NULL COMMENT '评论人',
  root_id BIGINT NOT NULL COMMENT '一级评论 id；一级时等于自身 id',
  parent_id BIGINT NULL COMMENT '直接父评论；一级为空',
  content VARCHAR(2000) NOT NULL COMMENT '正文',
  like_count INT NOT NULL DEFAULT 0 COMMENT '获赞冗余',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 正常 1 删除',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  KEY idx_post_root (post_id, root_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论';

CREATE TABLE IF NOT EXISTS comment_like (
  user_id BIGINT NOT NULL COMMENT '点赞人',
  comment_id BIGINT NOT NULL COMMENT '评论 ID',
  created_at DATETIME(3) NOT NULL COMMENT '点赞时间',
  PRIMARY KEY (user_id, comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赞评论';

CREATE TABLE IF NOT EXISTS share_log (
  id BIGINT PRIMARY KEY COMMENT '分享记录 ID',
  user_id BIGINT NOT NULL COMMENT '分享人',
  post_id BIGINT NOT NULL COMMENT '笔记 ID',
  channel VARCHAR(16) NOT NULL COMMENT 'link / im',
  created_at DATETIME(3) NOT NULL COMMENT '分享时间',
  KEY idx_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分享记录';

CREATE TABLE IF NOT EXISTS outbox_event (
  id BIGINT PRIMARY KEY COMMENT '事件 ID',
  aggregate_type VARCHAR(32) NOT NULL COMMENT '如 post / comment',
  aggregate_id VARCHAR(64) NOT NULL COMMENT '聚合根 ID',
  event_type VARCHAR(64) NOT NULL COMMENT '如 post.liked',
  payload_json JSON NOT NULL COMMENT '事件正文',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待发送 1 已发送 2 失败',
  created_at DATETIME(3) NOT NULL COMMENT '写入时间',
  sent_at DATETIME(3) NULL COMMENT '发送成功时间',
  KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox';

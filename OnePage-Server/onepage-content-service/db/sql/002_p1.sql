-- =============================================================================
-- 库：onepage_content（先 USE onepage_content 再执行）
-- 阶段：P1 笔记、媒体关联、Outbox、赞藏评、浏览（暂合在 content 库，避免第五个进程）
-- =============================================================================

CREATE TABLE IF NOT EXISTS post (
  id BIGINT PRIMARY KEY COMMENT '笔记 ID',
  author_id BIGINT NOT NULL COMMENT '作者 user_id',
  title VARCHAR(200) NULL COMMENT '标题，可空',
  body TEXT NOT NULL COMMENT '正文',
  post_type VARCHAR(16) NOT NULL COMMENT 'text / image / video / mixed',
  category VARCHAR(16) NOT NULL DEFAULT '' COMMENT '首页分类 旅行/美食/时尚/摄影/生活/运动/游戏/音乐',
  status VARCHAR(16) NOT NULL COMMENT 'draft / published / deleted',
  visibility TINYINT NOT NULL DEFAULT 0 COMMENT '0 公开（MVP 仅公开）',
  published_at DATETIME(3) NULL COMMENT '首次公开时间',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  deleted_at DATETIME(3) NULL COMMENT '软删时间',
  KEY idx_author_published (author_id, published_at),
  KEY idx_published (status, published_at, id),
  KEY idx_category_published (category, status, published_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记';

CREATE TABLE IF NOT EXISTS post_media (
  id BIGINT PRIMARY KEY COMMENT '关联 ID',
  post_id BIGINT NOT NULL COMMENT '笔记 ID',
  media_id BIGINT NOT NULL COMMENT 'media_asset.id',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '多图顺序，从小到大',
  role VARCHAR(16) NOT NULL COMMENT 'cover / gallery / video',
  KEY idx_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记与媒体';

CREATE TABLE IF NOT EXISTS outbox_event (
  id BIGINT PRIMARY KEY COMMENT '事件 ID',
  aggregate_type VARCHAR(32) NOT NULL COMMENT '如 post',
  aggregate_id VARCHAR(64) NOT NULL COMMENT '如笔记 ID',
  event_type VARCHAR(64) NOT NULL COMMENT '如 post.published',
  payload_json JSON NOT NULL COMMENT '事件正文',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待发送 1 已发送 2 失败',
  created_at DATETIME(3) NOT NULL COMMENT '写入时间',
  sent_at DATETIME(3) NULL COMMENT '发送成功时间',
  KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox';

CREATE TABLE IF NOT EXISTS post_like (
  user_id BIGINT NOT NULL COMMENT '点赞人',
  post_id BIGINT NOT NULL COMMENT '笔记 ID',
  created_at DATETIME(3) NOT NULL COMMENT '点赞时间',
  PRIMARY KEY (user_id, post_id),
  KEY idx_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赞笔记';

CREATE TABLE IF NOT EXISTS post_favorite (
  user_id BIGINT NOT NULL COMMENT '收藏人',
  post_id BIGINT NOT NULL COMMENT '笔记 ID',
  created_at DATETIME(3) NOT NULL COMMENT '收藏时间',
  PRIMARY KEY (user_id, post_id),
  KEY idx_post (post_id)
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
  KEY idx_post_root (post_id, root_id),
  KEY idx_post_parent (post_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论';

CREATE TABLE IF NOT EXISTS comment_like (
  user_id BIGINT NOT NULL COMMENT '点赞人',
  comment_id BIGINT NOT NULL COMMENT '评论 ID',
  created_at DATETIME(3) NOT NULL COMMENT '点赞时间',
  PRIMARY KEY (user_id, comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赞评论';

CREATE TABLE IF NOT EXISTS post_view (
  user_id BIGINT NOT NULL COMMENT '浏览人',
  post_id BIGINT NOT NULL COMMENT '笔记 ID',
  viewed_at DATETIME(3) NOT NULL COMMENT '最近浏览时间',
  PRIMARY KEY (user_id, post_id),
  KEY idx_user_viewed (user_id, viewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览历史（P1 暂放 content）';

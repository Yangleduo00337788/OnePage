-- =============================================================================
-- 库：onepage_content
-- 落地：P0 仅 media_asset
-- 待建：post、post_media、outbox（P1 发帖）
-- 执行：USE onepage_content;
-- =============================================================================

CREATE TABLE IF NOT EXISTS media_asset (
  id BIGINT PRIMARY KEY COMMENT '对外 mediaId',
  owner_id BIGINT NOT NULL COMMENT '上传者 user_id',
  bucket VARCHAR(64) NOT NULL COMMENT 'MinIO 桶名',
  object_key VARCHAR(512) NOT NULL COMMENT '对象键',
  mime_type VARCHAR(128) NOT NULL COMMENT '如 image/jpeg、video/mp4',
  size_bytes BIGINT NOT NULL COMMENT '字节数',
  duration_sec INT NULL COMMENT '视频时长秒；图片空',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 上传中 1 就绪 2 失败',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  KEY idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='媒体对象';

-- P1：笔记正文。发布时与 outbox_event 同一事务提交。
CREATE TABLE IF NOT EXISTS post (
  id BIGINT PRIMARY KEY COMMENT '笔记 ID',
  author_id BIGINT NOT NULL COMMENT '作者 user_id',
  title VARCHAR(200) NULL COMMENT '标题，可空',
  body TEXT NOT NULL COMMENT '正文',
  post_type VARCHAR(16) NOT NULL COMMENT 'text / image / video / mixed',
  status VARCHAR(16) NOT NULL COMMENT 'draft / published / deleted',
  visibility TINYINT NOT NULL DEFAULT 0 COMMENT '0 公开（MVP 仅公开）',
  published_at DATETIME(3) NULL COMMENT '首次公开时间',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  deleted_at DATETIME(3) NULL COMMENT '软删时间',
  KEY idx_author_published (author_id, published_at)
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

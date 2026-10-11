-- =============================================================================
-- 库：onepage_content（先 USE onepage_content 再执行）
-- 阶段：P0 仅媒体对象。P1 笔记/赞藏评见 002_p1.sql。
-- =============================================================================

-- 一次上传对应一条记录。浏览器直传 MinIO 后 complete 把 status 置就绪。
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

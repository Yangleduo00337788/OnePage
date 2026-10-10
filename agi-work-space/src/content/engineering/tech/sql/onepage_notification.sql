-- =============================================================================
-- 库：onepage_notification（P2 通知收件箱）
-- type：comment / reply / mention / like_post / favorite_post / like_comment / follow
-- 执行：USE onepage_notification;
-- =============================================================================

CREATE TABLE IF NOT EXISTS notification (
  id BIGINT PRIMARY KEY COMMENT '通知 ID',
  user_id BIGINT NOT NULL COMMENT '收件人',
  type VARCHAR(32) NOT NULL COMMENT '见文件头枚举',
  actor_id BIGINT NULL COMMENT '触发者；系统通知可空',
  aggregate_key VARCHAR(128) NOT NULL COMMENT '去重键，如 post:1:like:2',
  post_id BIGINT NULL COMMENT '相关笔记',
  comment_id BIGINT NULL COMMENT '相关评论',
  payload JSON NULL COMMENT '扩展字段',
  read_at DATETIME(3) NULL COMMENT '已读时间；空=未读',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  KEY idx_user_created (user_id, created_at),
  KEY idx_aggregate (user_id, aggregate_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知';

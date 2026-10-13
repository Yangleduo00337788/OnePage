-- onepage_notification：通知收件箱。已有库必须执行，否则通知接口失败。可重复执行。

CREATE DATABASE IF NOT EXISTS onepage_notification DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE onepage_notification;

CREATE TABLE IF NOT EXISTS notification (
  id BIGINT NOT NULL COMMENT '通知 ID',
  user_id BIGINT NOT NULL COMMENT '收件人',
  type VARCHAR(32) NOT NULL COMMENT 'comment/reply/mention/like_post/favorite_post/like_comment/follow',
  actor_id BIGINT NULL COMMENT '触发者',
  aggregate_key VARCHAR(128) NOT NULL COMMENT '去重键，如同帖同人点赞',
  post_id BIGINT NULL COMMENT '相关笔记',
  comment_id BIGINT NULL COMMENT '相关评论',
  payload JSON NULL COMMENT '扩展',
  read_at DATETIME(3) NULL COMMENT '已读时间；空为未读',
  created_at DATETIME(3) NOT NULL COMMENT '创建或最近一次合并时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_aggregate (user_id, aggregate_key),
  KEY idx_user_created (user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知收件箱';

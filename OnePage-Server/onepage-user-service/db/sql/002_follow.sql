CREATE TABLE IF NOT EXISTS user_follow (
  follower_id BIGINT NOT NULL COMMENT '关注人 user_id',
  followee_id BIGINT NOT NULL COMMENT '被关注人 user_id',
  created_at DATETIME(3) NOT NULL COMMENT '关注时间',
  PRIMARY KEY (follower_id, followee_id),
  KEY idx_followee_created (followee_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单向关注';

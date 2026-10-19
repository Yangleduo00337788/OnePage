-- 取关历史。已有库必须执行，否则报表取关/7 日留存仍为 0。
CREATE TABLE IF NOT EXISTS user_unfollow_log (
  id BIGINT NOT NULL COMMENT '记录 ID',
  follower_id BIGINT NOT NULL COMMENT '取关人',
  followee_id BIGINT NOT NULL COMMENT '被取关人',
  followed_at DATETIME(3) NOT NULL COMMENT '当时关注边的创建时间',
  unfollowed_at DATETIME(3) NOT NULL COMMENT '取关时间',
  PRIMARY KEY (id),
  KEY idx_followee_unfollowed (followee_id, unfollowed_at),
  KEY idx_followee_followed (followee_id, followed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='取关日志';

-- 仅修正 user_unfollow_log 的 COMMENT（表结构不变）。Windows 须 mysql --default-character-set=utf8mb4，不要用 PowerShell 管道。
USE onepage_user;

ALTER TABLE user_unfollow_log COMMENT='取关日志';
ALTER TABLE user_unfollow_log
  MODIFY id BIGINT NOT NULL COMMENT '记录 ID',
  MODIFY follower_id BIGINT NOT NULL COMMENT '取关人',
  MODIFY followee_id BIGINT NOT NULL COMMENT '被取关人',
  MODIFY followed_at DATETIME(3) NOT NULL COMMENT '当时关注边的创建时间',
  MODIFY unfollowed_at DATETIME(3) NOT NULL COMMENT '取关时间';

-- 创作者直播总览峰值在线。已有库执行。列已存在时报 Duplicate column，可忽略。
ALTER TABLE live_session
  ADD COLUMN peak_viewer_count INT NOT NULL DEFAULT 0 COMMENT '本场心跳在场人数峰值' AFTER ended_at;

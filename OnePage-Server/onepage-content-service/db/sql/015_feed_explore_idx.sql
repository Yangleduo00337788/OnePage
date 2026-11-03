-- 发现流公开首页：visibility + 已发布 + 未删 + 展示时间。已有库执行。索引已存在可忽略 Duplicate key name。
ALTER TABLE post
  ADD KEY idx_feed_explore (visibility, status, deleted_at, published_at, id);

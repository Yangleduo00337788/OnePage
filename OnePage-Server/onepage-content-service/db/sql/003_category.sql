-- =============================================================================
-- 库：onepage_content（先 USE onepage_content 再执行）
-- 首页分类：旅行/美食/时尚/摄影/生活/运动/游戏/音乐
-- =============================================================================

ALTER TABLE post
  ADD COLUMN category VARCHAR(16) NOT NULL DEFAULT '' COMMENT '首页分类' AFTER post_type,
  ADD KEY idx_category_published (category, status, published_at, id);

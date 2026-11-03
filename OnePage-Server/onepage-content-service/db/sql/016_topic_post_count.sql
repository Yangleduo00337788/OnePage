-- 话题热度落列，目录不再 LIKE 扫 post.body。已有库执行。列已存在可忽略 Duplicate column。
ALTER TABLE topic
  ADD COLUMN post_count BIGINT NOT NULL DEFAULT 0 COMMENT '公开已发布正文含 #名 的大约条数' AFTER enabled;

UPDATE topic t
SET post_count = (
  SELECT COUNT(*) FROM post p
  WHERE p.deleted_at IS NULL
    AND p.status = 'published'
    AND p.visibility = 0
    AND p.body LIKE CONCAT('%#', t.name, '%')
);

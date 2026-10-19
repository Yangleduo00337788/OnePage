-- 创作者首页平台公告。已有库必须执行。
CREATE TABLE IF NOT EXISTS platform_notice (
  id BIGINT NOT NULL COMMENT '公告 ID',
  title VARCHAR(64) NOT NULL COMMENT '标题',
  body VARCHAR(500) NOT NULL DEFAULT '' COMMENT '正文',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '列表顺序，小的在前',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 展示 0 停用',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_enabled_sort (enabled, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='创作者平台公告';

INSERT IGNORE INTO platform_notice (id, title, body, sort_order, enabled, created_at) VALUES
  (1, '一页公约', '真诚分享，友好互动', 1, 1, NOW(3));

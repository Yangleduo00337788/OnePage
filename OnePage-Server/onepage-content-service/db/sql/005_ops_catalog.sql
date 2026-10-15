-- 运营目录：分类 / 活动 / 话题。已有库执行本脚本。可重复：表 IF NOT EXISTS，种子 INSERT IGNORE。
ALTER TABLE post
  MODIFY COLUMN category VARCHAR(32) NOT NULL DEFAULT '' COMMENT '首页分类，对应 post_category.name';

CREATE TABLE IF NOT EXISTS post_category (
  id BIGINT NOT NULL COMMENT '分类 ID',
  name VARCHAR(32) NOT NULL COMMENT '展示名，写入 post.category',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '首页 Tab 顺序，小的在前',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 展示 0 停用',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记分类';

CREATE TABLE IF NOT EXISTS campaign (
  id VARCHAR(32) NOT NULL COMMENT '活动 id，写入 post.event_id',
  title VARCHAR(64) NOT NULL COMMENT '标题，发布时快照到 post.event_title',
  intro VARCHAR(200) NOT NULL DEFAULT '' COMMENT '简介',
  cover_url VARCHAR(512) NOT NULL DEFAULT '' COMMENT '封面 URL',
  status VARCHAR(16) NOT NULL DEFAULT 'live' COMMENT 'live 进行中 / upcoming 预告 / ended 结束',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '列表顺序',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 可关联 0 停用',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='官方活动';

CREATE TABLE IF NOT EXISTS topic (
  id BIGINT NOT NULL COMMENT '话题 ID',
  name VARCHAR(32) NOT NULL COMMENT '无 # 的话题名',
  official TINYINT NOT NULL DEFAULT 0 COMMENT '1 官方 0 用户创建',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '官方排序',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 可联想 0 停用',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题';

INSERT IGNORE INTO post_category (id, name, sort_order, enabled, created_at) VALUES
  (1, '旅行', 1, 1, NOW(3)),
  (2, '美食', 2, 1, NOW(3)),
  (3, '时尚', 3, 1, NOW(3)),
  (4, '摄影', 4, 1, NOW(3)),
  (5, '生活', 5, 1, NOW(3)),
  (6, '运动', 6, 1, NOW(3)),
  (7, '游戏', 7, 1, NOW(3)),
  (8, '音乐', 8, 1, NOW(3));

INSERT IGNORE INTO campaign (id, title, intro, cover_url, status, sort_order, enabled, created_at) VALUES
  ('e1', '春季创作激励计划', '优质笔记可获得流量券。', '', 'live', 1, 1, NOW(3)),
  ('e2', '周末话题挑战 · 一页日常', '带指定话题发布。', '', 'live', 2, 1, NOW(3));

INSERT IGNORE INTO topic (id, name, official, sort_order, enabled, created_at) VALUES
  (1, '一页日常', 1, 1, 1, NOW(3)),
  (2, '周末随拍', 1, 2, 1, NOW(3)),
  (3, '探店', 1, 3, 1, NOW(3));

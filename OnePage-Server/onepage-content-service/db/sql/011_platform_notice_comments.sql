-- 仅修正 platform_notice 的 COMMENT（表结构不变）。Windows 须 mysql --default-character-set=utf8mb4，不要用 PowerShell 管道。
USE onepage_content;

ALTER TABLE platform_notice COMMENT='创作者平台公告';
ALTER TABLE platform_notice
  MODIFY id BIGINT NOT NULL COMMENT '公告 ID',
  MODIFY title VARCHAR(64) NOT NULL COMMENT '标题',
  MODIFY body VARCHAR(500) NOT NULL DEFAULT '' COMMENT '正文',
  MODIFY sort_order INT NOT NULL DEFAULT 0 COMMENT '列表顺序，小的在前',
  MODIFY enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 展示 0 停用',
  MODIFY created_at DATETIME(3) NOT NULL COMMENT '创建时间';

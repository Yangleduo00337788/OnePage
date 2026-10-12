-- =============================================================================
-- 库：onepage_content
-- 已有 post 表补原创/类型/活动；visibility 支持仅自己
-- 列已存在时跳过，可重复执行
-- =============================================================================

SET @db := DATABASE();

SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'post' AND COLUMN_NAME = 'original'
    ),
    'SELECT 1',
    'ALTER TABLE post ADD COLUMN original TINYINT NOT NULL DEFAULT 0 COMMENT ''1 声明原创'' AFTER category'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'post' AND COLUMN_NAME = 'content_kind'
    ),
    'SELECT 1',
    'ALTER TABLE post ADD COLUMN content_kind VARCHAR(16) NOT NULL DEFAULT '''' COMMENT ''空=不声明；虚构创作/个人观点/专业内容'' AFTER original'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'post' AND COLUMN_NAME = 'event_id'
    ),
    'SELECT 1',
    'ALTER TABLE post ADD COLUMN event_id VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''关联活动 id'' AFTER content_kind'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'post' AND COLUMN_NAME = 'event_title'
    ),
    'SELECT 1',
    'ALTER TABLE post ADD COLUMN event_title VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''关联活动标题快照'' AFTER event_id'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE post
  MODIFY visibility TINYINT NOT NULL DEFAULT 0 COMMENT '0 公开 1 仅自己';

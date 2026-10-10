-- =============================================================================
-- 库：onepage_live（P4 基础直播，无礼物）
-- 执行：USE onepage_live;
-- =============================================================================

CREATE TABLE IF NOT EXISTS live_room (
  id BIGINT PRIMARY KEY COMMENT '房间 ID',
  owner_id BIGINT NOT NULL COMMENT '主播 user_id',
  title VARCHAR(100) NOT NULL COMMENT '房间标题',
  cover_object_key VARCHAR(512) NULL COMMENT '封面 MinIO key',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  KEY idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播房间';

CREATE TABLE IF NOT EXISTS live_session (
  id BIGINT PRIMARY KEY COMMENT '场次 ID',
  room_id BIGINT NOT NULL COMMENT '房间 ID',
  stream_key VARCHAR(64) NOT NULL COMMENT '推流密钥（SRS）',
  status VARCHAR(16) NOT NULL COMMENT 'idle / live / ended',
  started_at DATETIME(3) NULL COMMENT '开播时间',
  ended_at DATETIME(3) NULL COMMENT '下播时间',
  KEY idx_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播场次';

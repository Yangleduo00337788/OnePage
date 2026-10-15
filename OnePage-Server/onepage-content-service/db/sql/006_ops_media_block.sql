-- 运营隐藏媒体。已有库执行。列已存在时跳过本脚本。
ALTER TABLE media_asset
  ADD COLUMN blocked_at DATETIME(3) NULL COMMENT '运营隐藏时间，空则对用户可见';

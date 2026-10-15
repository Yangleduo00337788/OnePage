-- 运营可隐藏私信。已有库执行。列已存在时跳过本脚本。
ALTER TABLE message
  ADD COLUMN deleted_at DATETIME(3) NULL COMMENT '运营隐藏时间，空则对用户可见';

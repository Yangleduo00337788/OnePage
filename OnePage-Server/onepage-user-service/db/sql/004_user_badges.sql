-- 主播入驻 / 大 V 标识。已有库必须执行。列已存在时跳过本脚本。
ALTER TABLE user_profile
  ADD COLUMN live_host TINYINT NOT NULL DEFAULT 0 COMMENT '1 主播入驻通过' AFTER gender;
ALTER TABLE user_profile
  ADD COLUMN verified TINYINT NOT NULL DEFAULT 0 COMMENT '1 大 V 认证通过' AFTER live_host;

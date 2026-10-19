-- 一页官方认证号。已有库必须执行。列已存在时跳过 ADD。Windows 须 mysql --default-character-set=utf8mb4。
ALTER TABLE user_profile
  ADD COLUMN official TINYINT NOT NULL DEFAULT 0 COMMENT '1 一页官方认证号' AFTER verified;

UPDATE user_profile SET official = 1 WHERE nickname = '一页官方';

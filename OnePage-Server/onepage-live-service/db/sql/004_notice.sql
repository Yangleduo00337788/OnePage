-- 本场公告。已有 onepage_live 另执行。列已存在会失败，只跑一次。

USE onepage_live;

ALTER TABLE live_room
  ADD COLUMN notice VARCHAR(200) NOT NULL DEFAULT '' COMMENT '本场公告，空则前端用默认欢迎文案' AFTER cover_object_key;

-- 聊天增加进房系统消息类型。已有 onepage_live 另执行。可重复：列已存在会失败，只跑一次。

USE onepage_live;

ALTER TABLE live_chat
  ADD COLUMN msg_type VARCHAR(16) NOT NULL DEFAULT 'text' COMMENT 'text 发言 join 进房' AFTER body;

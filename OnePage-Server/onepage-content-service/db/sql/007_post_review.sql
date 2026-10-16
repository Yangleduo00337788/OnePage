-- 创作者审稿。已有库执行。列已存在时跳过本脚本。
ALTER TABLE post
  MODIFY status VARCHAR(16) NOT NULL COMMENT 'draft / pending / published / rejected / deleted',
  ADD COLUMN reject_reason VARCHAR(200) NULL COMMENT '未通过原因，空则无' AFTER status;

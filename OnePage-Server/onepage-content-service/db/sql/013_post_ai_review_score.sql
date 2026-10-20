-- 机审风险分。已有库执行。列已存在时跳过本脚本。
ALTER TABLE post
  ADD COLUMN ai_review_score INT NULL COMMENT '机审风险分 0-100，空则未打分' AFTER ai_review_reason;

-- 公开笔记 AI 文字预审。已有库执行。列已存在时跳过本脚本。
ALTER TABLE post
  ADD COLUMN ai_review VARCHAR(16) NULL COMMENT '空=未跑；pass 机审过；flag 需人审；skip 未跑或失败' AFTER reject_reason,
  ADD COLUMN ai_review_reason VARCHAR(200) NULL COMMENT '机审未过或跳过原因' AFTER ai_review;

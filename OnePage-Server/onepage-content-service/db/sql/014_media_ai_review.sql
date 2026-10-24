-- 媒体机审结论；帖汇总标签。已有库执行。列已存在时跳过本脚本。
ALTER TABLE media_asset
  ADD COLUMN ai_review VARCHAR(16) NULL COMMENT '空=未跑；pass 过；flag 人审；reject 高信违规；skip 未跑或失败' AFTER blocked_at,
  ADD COLUMN ai_review_reason VARCHAR(200) NULL COMMENT '机审原因' AFTER ai_review,
  ADD COLUMN ai_review_score INT NULL COMMENT '机审风险分 0-100' AFTER ai_review_reason,
  ADD COLUMN ai_review_labels VARCHAR(64) NULL COMMENT '逗号分隔 normal,ad,porn,violence' AFTER ai_review_score;

ALTER TABLE post
  ADD COLUMN ai_review_labels VARCHAR(64) NULL COMMENT '汇总标签，逗号分隔' AFTER ai_review_score;

-- 创作者「创作字典」。已有库必须执行。可重复执行。
CREATE TABLE IF NOT EXISTS creator_faq (
  id BIGINT NOT NULL COMMENT '问答 ID',
  group_id VARCHAR(16) NOT NULL COMMENT 'account / create / data / faq',
  question VARCHAR(120) NOT NULL COMMENT '问题',
  answer VARCHAR(1000) NOT NULL COMMENT '回答',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '组内顺序，小的在前',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 展示 0 停用',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_group_sort (group_id, enabled, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='创作者创作字典';

INSERT INTO creator_faq (id, group_id, question, answer, sort_order, enabled, created_at) VALUES
  (1, 'account', '如何修改昵称和头像', '在社区站打开「我的」，点头像或编辑资料即可。创作者中心共用同一套登录账号。', 1, 1, NOW(3)),
  (2, 'account', '如何设置或修改密码', '在社区站侧栏「更多」点「设置」，可设密、改密、短信找回，以及绑定 GitHub / Google。', 2, 1, NOW(3)),
  (3, 'account', '退出登录', '点右上角头像与昵称，在弹出菜单里选择退出登录。', 3, 1, NOW(3)),
  (4, 'create', '如何发布笔记', '点左栏红色「发布笔记」，或悬停选择图文、视频、长文。', 1, 1, NOW(3)),
  (5, 'create', '笔记状态说明', '已发布可被推荐；审核中暂不可见；草稿仅自己可见。', 2, 1, NOW(3)),
  (6, 'create', '直播入口', '左栏「直播」或首页「去开播」填写标题，可裁一张 3:4 封面。OBS 推流后点「进入直播间」到社区观看。关播在创作者中心或直播间确认。社区直播页只看不播。', 3, 1, NOW(3)),
  (7, 'data', '账号概览看什么', '近 7 日浏览、新增关注、互动和已发布数量，用来判断账号整体热度。', 1, 1, NOW(3)),
  (8, 'data', '内容分析看什么', '单篇笔记的浏览、点赞、评论，便于找出表现更好的内容。', 2, 1, NOW(3)),
  (9, 'data', '粉丝数据看什么', '近 7 日净涨粉、留存，以及粉丝看过的笔记。数字来自关注与浏览记录。', 3, 1, NOW(3)),
  (10, 'faq', '为什么和社区是两个网站', '创作者中心是后台，社区是浏览站。地址栏切换端口或域名即可，互不覆盖。', 1, 1, NOW(3)),
  (11, 'faq', '数据什么时候是真的', '总览和报表已接真实浏览、互动、涨粉与直播峰值。礼物人数暂为 0。', 2, 1, NOW(3)),
  (12, 'faq', '活动怎么参加', '打开「活动中心」查看官方活动，按活动页说明报名或投稿。', 3, 1, NOW(3))
ON DUPLICATE KEY UPDATE
  group_id = VALUES(group_id),
  question = VALUES(question),
  answer = VALUES(answer),
  sort_order = VALUES(sort_order),
  enabled = VALUES(enabled);

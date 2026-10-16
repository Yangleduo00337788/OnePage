-- 可选 Docker 初始化：P0 + P1 content 表。字段注释与各服务 db/sql 保持一致。
-- 表结构以各服务 db/sql 与工作空间同名文档为准。

USE onepage_auth;

CREATE TABLE IF NOT EXISTS auth_account (
  id BIGINT PRIMARY KEY COMMENT '全局 user_id，注册时生成',
  phone VARCHAR(20) NULL COMMENT '手机号，国内 11 位或 E.164',
  email VARCHAR(128) NULL COMMENT '邮箱',
  password_hash VARCHAR(255) NULL COMMENT 'BCrypt；纯 OAuth 可空',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 正常 1 冻结',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_phone (phone),
  UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录账号';

CREATE TABLE IF NOT EXISTS auth_oauth_binding (
  id BIGINT PRIMARY KEY COMMENT '绑定记录 ID',
  user_id BIGINT NOT NULL COMMENT '对应 auth_account.id / 用户资料 user_id',
  provider VARCHAR(16) NOT NULL COMMENT 'github / google',
  provider_user_id VARCHAR(64) NOT NULL COMMENT '第三方侧用户唯一 ID',
  created_at DATETIME(3) NOT NULL COMMENT '绑定时间',
  UNIQUE KEY uk_provider_user (provider, provider_user_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth 绑定';

USE onepage_user;

CREATE TABLE IF NOT EXISTS user_profile (
  user_id BIGINT PRIMARY KEY COMMENT '与 auth_account.id 一致',
  page_id VARCHAR(16) NOT NULL COMMENT '一页号，对外展示，创建后不可改',
  nickname VARCHAR(64) NOT NULL COMMENT '昵称',
  avatar_object_key VARCHAR(512) NULL COMMENT '头像 MinIO object key',
  cover_object_key VARCHAR(512) NULL COMMENT '主页封面 MinIO object key',
  bio VARCHAR(500) NULL COMMENT '签名',
  gender TINYINT NOT NULL DEFAULT 0 COMMENT '0 未知 1 男 2 女',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_page_id (page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资料';

CREATE TABLE IF NOT EXISTS user_stat (
  user_id BIGINT PRIMARY KEY COMMENT '与 user_profile.user_id 一致',
  following_count INT NOT NULL DEFAULT 0 COMMENT '关注数',
  follower_count INT NOT NULL DEFAULT 0 COMMENT '粉丝数',
  like_received_count INT NOT NULL DEFAULT 0 COMMENT '笔记获赞数',
  favorite_received_count INT NOT NULL DEFAULT 0 COMMENT '笔记被藏数',
  updated_at DATETIME(3) NOT NULL COMMENT '最后累加时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户计数';

CREATE TABLE IF NOT EXISTS user_follow (
  follower_id BIGINT NOT NULL COMMENT '关注人 user_id',
  followee_id BIGINT NOT NULL COMMENT '被关注人 user_id',
  created_at DATETIME(3) NOT NULL COMMENT '关注时间',
  PRIMARY KEY (follower_id, followee_id),
  KEY idx_followee_created (followee_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单向关注';

USE onepage_content;

CREATE TABLE IF NOT EXISTS media_asset (
  id BIGINT PRIMARY KEY COMMENT '对外 mediaId',
  owner_id BIGINT NOT NULL COMMENT '上传者 user_id',
  bucket VARCHAR(64) NOT NULL COMMENT 'MinIO 桶名，默认 onepage-media',
  object_key VARCHAR(512) NOT NULL COMMENT '对象键',
  mime_type VARCHAR(128) NOT NULL COMMENT '如 image/jpeg',
  size_bytes BIGINT NOT NULL COMMENT '字节数',
  duration_sec INT NULL COMMENT '视频时长秒；图片空',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 上传中 1 就绪 2 失败',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  blocked_at DATETIME(3) NULL COMMENT '运营隐藏时间，空则对用户可见',
  KEY idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='媒体对象';

-- P1 合在 content 库（DEC-023），与 002_p1.sql 一致。
CREATE TABLE IF NOT EXISTS post (
  id BIGINT PRIMARY KEY COMMENT '笔记 ID',
  author_id BIGINT NOT NULL COMMENT '作者 user_id',
  title VARCHAR(200) NULL COMMENT '标题，可空',
  body TEXT NOT NULL COMMENT '正文',
  post_type VARCHAR(16) NOT NULL COMMENT 'text / image / video / mixed',
  category VARCHAR(16) NOT NULL DEFAULT '' COMMENT '首页分类 旅行/美食/时尚/摄影/生活/运动/游戏/音乐',
  original TINYINT NOT NULL DEFAULT 0 COMMENT '1 声明原创',
  content_kind VARCHAR(16) NOT NULL DEFAULT '' COMMENT '空=不声明；虚构创作/个人观点/专业内容',
  event_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '关联活动 id',
  event_title VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联活动标题快照',
  status VARCHAR(16) NOT NULL COMMENT 'draft / pending / published / rejected / deleted',
  reject_reason VARCHAR(200) NULL COMMENT '未通过原因，空则无',
  visibility TINYINT NOT NULL DEFAULT 0 COMMENT '0 公开 1 仅自己',
  published_at DATETIME(3) NULL COMMENT '首次公开时间',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  deleted_at DATETIME(3) NULL COMMENT '软删时间',
  KEY idx_author_published (author_id, published_at),
  KEY idx_published (status, published_at, id),
  KEY idx_category_published (category, status, published_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记';

CREATE TABLE IF NOT EXISTS post_media (
  id BIGINT PRIMARY KEY COMMENT '关联 ID',
  post_id BIGINT NOT NULL COMMENT '笔记 ID',
  media_id BIGINT NOT NULL COMMENT 'media_asset.id',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '多图顺序，从小到大',
  role VARCHAR(16) NOT NULL COMMENT 'cover / gallery / video',
  KEY idx_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记与媒体';

CREATE TABLE IF NOT EXISTS outbox_event (
  id BIGINT PRIMARY KEY COMMENT '事件 ID',
  aggregate_type VARCHAR(32) NOT NULL COMMENT '如 post',
  aggregate_id VARCHAR(64) NOT NULL COMMENT '如笔记 ID',
  event_type VARCHAR(64) NOT NULL COMMENT '如 post.published',
  payload_json JSON NOT NULL COMMENT '事件正文',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待发送 1 已发送 2 失败',
  created_at DATETIME(3) NOT NULL COMMENT '写入时间',
  sent_at DATETIME(3) NULL COMMENT '发送成功时间',
  KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox';

CREATE TABLE IF NOT EXISTS post_like (
  user_id BIGINT NOT NULL COMMENT '点赞人',
  post_id BIGINT NOT NULL COMMENT '笔记 ID',
  created_at DATETIME(3) NOT NULL COMMENT '点赞时间',
  PRIMARY KEY (user_id, post_id),
  KEY idx_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赞笔记';

CREATE TABLE IF NOT EXISTS post_favorite (
  user_id BIGINT NOT NULL COMMENT '收藏人',
  post_id BIGINT NOT NULL COMMENT '笔记 ID',
  created_at DATETIME(3) NOT NULL COMMENT '收藏时间',
  PRIMARY KEY (user_id, post_id),
  KEY idx_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='藏笔记';

CREATE TABLE IF NOT EXISTS comment (
  id BIGINT PRIMARY KEY COMMENT '评论 ID',
  post_id BIGINT NOT NULL COMMENT '笔记 ID',
  user_id BIGINT NOT NULL COMMENT '评论人',
  root_id BIGINT NOT NULL COMMENT '一级评论 id；一级时等于自身 id',
  parent_id BIGINT NULL COMMENT '直接父评论；一级为空',
  content VARCHAR(2000) NOT NULL COMMENT '正文',
  like_count INT NOT NULL DEFAULT 0 COMMENT '获赞冗余',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 正常 1 删除',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  KEY idx_post_root (post_id, root_id),
  KEY idx_post_parent (post_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论';

CREATE TABLE IF NOT EXISTS comment_like (
  user_id BIGINT NOT NULL COMMENT '点赞人',
  comment_id BIGINT NOT NULL COMMENT '评论 ID',
  created_at DATETIME(3) NOT NULL COMMENT '点赞时间',
  PRIMARY KEY (user_id, comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赞评论';

CREATE TABLE IF NOT EXISTS post_view (
  user_id BIGINT NOT NULL COMMENT '浏览人',
  post_id BIGINT NOT NULL COMMENT '笔记 ID',
  viewed_at DATETIME(3) NOT NULL COMMENT '最近浏览时间',
  PRIMARY KEY (user_id, post_id),
  KEY idx_user_viewed (user_id, viewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览历史（P1 暂放 content）';

USE onepage_notification;

CREATE TABLE IF NOT EXISTS notification (
  id BIGINT NOT NULL COMMENT '通知 ID',
  user_id BIGINT NOT NULL COMMENT '收件人',
  type VARCHAR(32) NOT NULL COMMENT 'comment/reply/mention/like_post/favorite_post/like_comment/follow',
  actor_id BIGINT NULL COMMENT '触发者',
  aggregate_key VARCHAR(128) NOT NULL COMMENT '去重键，如同帖同人点赞',
  post_id BIGINT NULL COMMENT '相关笔记',
  comment_id BIGINT NULL COMMENT '相关评论',
  payload JSON NULL COMMENT '扩展',
  read_at DATETIME(3) NULL COMMENT '已读时间；空为未读',
  created_at DATETIME(3) NOT NULL COMMENT '创建或最近一次合并时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_aggregate (user_id, aggregate_key),
  KEY idx_user_created (user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知收件箱';

USE onepage_message;

CREATE TABLE IF NOT EXISTS conversation (
  id BIGINT NOT NULL COMMENT '会话 ID',
  type VARCHAR(16) NOT NULL COMMENT 'single',
  pair_key VARCHAR(64) NULL COMMENT '单聊双方 userId 升序拼接，防重复开聊',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '最后消息时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_pair (pair_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话';

CREATE TABLE IF NOT EXISTS conversation_member (
  conversation_id BIGINT NOT NULL COMMENT '会话 ID',
  user_id BIGINT NOT NULL COMMENT '成员',
  last_read_message_id BIGINT NULL COMMENT '已读到的消息 ID',
  joined_at DATETIME(3) NOT NULL COMMENT '加入时间',
  PRIMARY KEY (conversation_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话成员';

CREATE TABLE IF NOT EXISTS message (
  id BIGINT NOT NULL COMMENT '消息 ID',
  conversation_id BIGINT NOT NULL COMMENT '会话 ID',
  sender_id BIGINT NOT NULL COMMENT '发送人',
  msg_type VARCHAR(16) NOT NULL COMMENT 'text / image / post_card / live_card',
  content JSON NOT NULL COMMENT '文本或卡片',
  created_at DATETIME(3) NOT NULL COMMENT '发送时间',
  deleted_at DATETIME(3) NULL COMMENT '运营隐藏时间，空则对用户可见',
  PRIMARY KEY (id),
  KEY idx_conv_id (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信消息';

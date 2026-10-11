# onepage_content

| 环境 | 脚本 | 说明 |
| --- | --- | --- |
| local | 001_init.sql | P0：媒体对象。工作空间：`sql/onepage-content-service/001_init.md` |
| local | 002_p1.sql | P1：笔记 / 媒体关联 / Outbox / 赞藏评 / 浏览。工作空间同名 md |
| local | 002_p1_fix_comments.sql | 修正 002 用错误码页写入的中文 COMMENT |
| local | 003_category.sql | `post.category`：首页分类 |

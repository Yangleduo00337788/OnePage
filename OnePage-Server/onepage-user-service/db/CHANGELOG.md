# onepage_user

| 环境 | 脚本 | 说明 |
| --- | --- | --- |
| local | 001_init.sql | P0：资料与计数 |
| local | 002_follow.sql | P2：`user_follow`。已有库必须执行，否则关注接口失败 |
| local | 003_unfollow_log.sql | 取关日志。已有库必须执行，否则报表取关/7 日留存仍为 0 |

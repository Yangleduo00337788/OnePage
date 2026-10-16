---
title: Java 开发规约
description: Agent 改 OnePage-Server 必读；章节对齐阿里 Java 手册，正文为本项目约定
order: 2
---

由仓库根 `OnePage/AGENTS.md` **链式引用**：Agent 读完根 AGENTS 后必须再用 Read 打开本文。改 `OnePage-Server` 的 Java / Mapper XML / 服务 yml 按「必须」执行。官方《阿里巴巴 Java 开发手册》受版权保护，**禁止把 PDF/纸书全文粘进本仓**。本文覆盖同一套章节，写成 ONEPAGE 可执行约定。IDE 可用 [P3C](https://github.com/alibaba/p3c)（Apache-2.0）做静态检查，与本文冲突时以本文和 [接口契约](/engineering/process/contracts) 为准。

> [!NOTE]
> 事实源：本文（Java 写法）+ 契约/表结构 MD（产品事实）。不要只在代码注释里藏接口字段。

## Agent 怎么用

1. 动手前打开本文件；不要凭记忆发明第二套命名或分层。
2. 新增类放对包：`web` HTTP、`service` 编排、`client` Feign、`mapper`/`entity` 表、`config` Bean。
3. 内网身份只用网关 `X-User-Id`，业务服务不解析 Cookie。
4. 改接口、表、服务边界同一轮回写工作空间知识空间，见 `OnePage/AGENTS.md` 与 harness `AGENTS.md` 里的回写表。禁止只改 Java。

---

## 1 编程规约

### 1.1 命名

| 级别 | 约定 |
| --- | --- |
| 必须 | 类名 UpperCamelCase；方法/变量 lowerCamelCase；常量全大写下划线；包名全小写，不用下划线 |
| 必须 | 抽象类 `Abstract` 或 `Base` 前缀；异常以 `Exception` 结尾；测试类 `*Test` |
| 必须 | 接口 `I` 前缀不要用。实现类可用 `Impl` 后缀。枚举成员全大写下划线 |
| 必须 | 禁止拼音与英文混用（`DaZhePromotion` 这种）、禁止 `a1`/`tmp` 当业务名 |
| 必须 | 布尔不要用 `is` 当字段名（Jackson / 部分框架生成 `isIsXxx`）。实体用 `liked`、`hasPassword` |
| 推荐 | 分层后缀与目录一致：`PostController`、`PostService`、`PostMapper`、`UserClient` |
| 推荐 | 集合名词用复数语义：`mediaIds`、`items`；DTO 用 `*Request` / `*View` / `*Dtos` 内部 record |

ONEPAGE：对外 JSON 字段跟 [API 明细](/engineering/process/api-endpoints)，Java 用 record/DTO 映射，不要为了「美观」改 JSON 名。

### 1.2 常量

| 级别 | 约定 |
| --- | --- |
| 必须 | 不许魔法值散落：超时、场景码、Cookie 名、错误码放常量或枚举 |
| 必须 | `long` 字面量写 `L`：`1000L`，不要 `1000l` |
| 推荐 | 相关常量进枚举或一类 `XxxConstants`，不要一个类堆全服务常量 |
| 推荐 | 未赋值固定字面用 `static final`；可变配置走环境变量 / yml `${}`，禁止写死密码 |

ONEPAGE：C 端 Cookie `ONEPAGE_SESSION`、运营 Cookie `ONEPAGE_OPS_SESSION`（DEC-046），内部令牌 `onepage.internal-token`，不要再造别名。Session 超时只写 yml `spring.session.timeout: ${SESSION_TIMEOUT}`（Boot 自动配置），**禁止**启动类 `@EnableRedisHttpSession` / `@EnableRedisWebSession`（注解默认 1800 秒会盖掉环境变量）。

### 1.3 代码格式

| 级别 | 约定 |
| --- | --- |
| 必须 | 单文件 UTF-8；缩进空格（与现有 Java 文件一致，当前多为 2 空格则保持，不要单文件改成 4） |
| 必须 | 单行不超过约 120 字符，换行在运算符/逗号后 |
| 必须 | `{` 不换行；`}` 单独一行，除非空块 `{}` |
| 必须 | 运算符两侧空格；逗号后空格 |
| 推荐 | 方法之间空一行；不要连续空多行 |
| 推荐 | import 不用 `*`；不用无用 import |

### 1.4 OOP

| 级别 | 约定 |
| --- | --- |
| 必须 | 覆写必须 `@Override` |
| 必须 | 不能用过时 API（能编译过也不要新写 `Date` 当业务时间，见 1.5） |
| 必须 | 对外暴露的方法参数不要改入参集合内容；要改就先拷贝或返回新对象 |
| 必须 | 比较整数包装类型用 `equals`，不要 `==`（除缓存过的常量区间外，一律 `equals` / `Objects.equals`） |
| 必须 | POJO 不要在 getter 里搞业务；构造后字段靠显式赋值 |
| 推荐 | 构造器参数过多时用建造或明确工厂方法 |
| 推荐 | 接口保持小而稳；不要为「以后可能」加空方法 |
| 允许 | MyBatis-Plus 实体保留 setter，与现有代码一致 |

ONEPAGE：Controller 只做参数与鉴权头读取，业务在 `service`。Feign 接口放 `client`。Feign 默认 JDK 客户端不能发 PATCH，内网更新用 POST（对外 `/api/v1/ops` 仍可 PATCH）。

### 1.5 日期时间

| 级别 | 约定 |
| --- | --- |
| 必须 | 业务时间用 `java.time`（`Instant` / `LocalDateTime` 等），不要新写 `java.util.Date` / `Calendar` |
| 必须 | 禁止 `yyyy` 与 `YYYY` 混用（周年）；格式化用明确 `DateTimeFormatter`，不要 `SimpleDateFormat` 当共享变量 |
| 必须 | 与前端约定 ISO-8601 字符串；库内时间戳列类型见 DDL，不要隐式时区转换 |
| 推荐 | 游标时间用 epoch millis 拼进 cursor（Feed 已是 `{publishedAtMillis}_{postId}`） |

### 1.6 集合

| 级别 | 约定 |
| --- | --- |
| 必须 | `foreach` 里不要增删当前集合；删除用迭代器或收集后再删 |
| 必须 | 判断集合空用 `isEmpty()`，不要 `size() == 0` 当唯一写法 |
| 必须 | `List` 转数组指定类型；不要假设 `subList` / `asList` 可结构修改 |
| 必须 | 不要用 `equals` 比较不同实现却依赖顺序的集合当集合相等（Set vs List） |
| 推荐 | 返回空集合用 `List.of()` / `Collections.emptyList()`，不要 `null` |
| 推荐 | 预估容量时给 `HashMap` 初始容量，避免无谓扩容 |

### 1.7 并发

| 级别 | 约定 |
| --- | --- |
| 必须 | 线程池用 `ThreadPoolExecutor` 显式构造，禁止 `Executors` 的无界队列工厂（容易 OOM） |
| 必须 | 共享可变状态要有明确同步或单线程；不要靠「现在是单实例」赌 |
| 必须 | `SimpleDateFormat` 不要当静态共享；用 `DateTimeFormatter`（不可变） |
| 必须 | 锁顺序全局一致，避免死锁；`tryLock` 必须处理失败 |
| 推荐 | P1 不在请求线程里打 MQ。Outbox 与业务同一事务写入即可 |
| 允许 | Web 请求默认 Tomcat 线程；不要为每个请求 `new Thread` |

### 1.8 控制语句

| 级别 | 约定 |
| --- | --- |
| 必须 | `if/else/for/while/do` 即使单行也加大括号 |
| 必须 | 不要用 `switch` 穿透漏 `break`；`switch` 枚举覆盖所有分支或 `default` 抛错 |
| 必须 | 高复杂度方法拆私有方法；嵌套不超过约 3 层 |
| 推荐 | 卫语句早返回，少 `else` |
| 推荐 | 布尔表达式抽成有含义的方法名 |

### 1.9 注释

| 级别 | 约定 |
| --- | --- |
| 必须 | 类上用中文说明职责和边界（例如「不解析 Cookie」） |
| 必须 | 注释写为什么，不复述代码 |
| 必须 | 注释与代码同步改 |
| 推荐 | 非常见注解（`@RequestMapping` 前缀、Feign、内部 Filter）说明给谁用 |
| 禁止 | 每个 getter、每个 `if` 一行废话；禁止把契约字段表只写在注释 |

### 1.10 其他

| 级别 | 约定 |
| --- | --- |
| 必须 | 正则预编译进 `static final Pattern`，不要热路径每次 `compile` |
| 必须 | 浮点比较用误差或整数分（钱、计数用整数） |
| 必须 | `@PathVariable("postId")` **必须写名字**（Boot 3；parent 已开 `-parameters` 仍写名字） |
| 推荐 | 及时关掉必须关的资源：用 try-with-resources |
| 推荐 | 不要 `catch` 后空着；见第 2 章 |

---

## 2 异常与日志

| 级别 | 约定 |
| --- | --- |
| 必须 | 对外统一 `{ code, message, requestId }`，用 `common` 的错误码，不要把堆栈甩给浏览器 |
| 必须 | 可恢复与不可恢复分开：参数问题 400，未登录 401，不存在 404，无权限 403 |
| 必须 | 捕获后若不能处理，要带上下文再抛；禁止 `e.printStackTrace()` |
| 必须 | 不要吞掉异常后返回成功 |
| 必须 | 日志用占位符 `log.warn("post {} missing", id)`，不要字符串相加拼大对象 |
| 必须 | 禁止日志打印密码、验证码、Cookie、内部 token、`.env` 值 |
| 推荐 | 异常类型具体，不要一律 `Exception`；自定义异常带错误码 |
| 推荐 | 边界（鉴权失败、Outbox 写入失败）打 warn/error，热路径成功不要 info 刷屏 |

ONEPAGE：网关登出不要再转发 auth 导致挂起；这类坑写进契约，代码按契约做。

---

## 3 单元测试

| 级别 | 约定 |
| --- | --- |
| 必须 | 测试类 `src/test/java`，名称 `*Test`，方法能说明场景 |
| 必须 | 断言明确期望；禁止无断言的「跑过就算」 |
| 必须 | 单测不依赖真实 Nacos / 本机 MySQL 才算单元测试；联调用文档里的六进程 |
| 推荐 | 先测 service 规则（分类必填、自己的帖、cursor 解析），Controller 只测绑定 |
| 推荐 | 边界：空列表、未登录公开 GET、无权限删帖 |
| 允许 | P1 可以先少测，但新写分支逻辑不要零覆盖 |

---

## 4 安全

| 级别 | 约定 |
| --- | --- |
| 必须 | SQL 用参数绑定（MyBatis `#{}`），禁止 `${}` 拼 SQL、禁止拼接用户输入进 WHERE |
| 必须 | 写接口未登录 401；公开 GET 仍可注入 `X-User-Id` 填 `likedByMe` |
| 必须 | 内部接口校验 `X-Internal-Token`，与 env 一致 |
| 必须 | 密码 BCrypt；验证码走 Redis；短信 mock 只本地 |
| 必须 | 水平越权：删帖/评只能作者（或后续管理员），用 `X-User-Id` 比，不要信 body 里的 userId |
| 必须 | 上传校验 `purpose`、大小、mime；complete 只认本人 media |
| 必须 | 机密只在环境变量；yml 只 `${VAR}` |
| 推荐 | 用户正文输出按前端转义；后端仍要限制长度 |
| 推荐 | 上线后再定 CSRF；本地 SameSite=Lax |

---

## 5 MySQL

| 级别 | 约定 |
| --- | --- |
| 必须 | 改表先改工作空间 [库表](/engineering/tech/database-schema) 与 [SQL](/engineering/tech/sql)（文档名必须与 Server `*.sql` 同名，禁止自编 `onepage_xxx.sql`），再改 `OnePage-Server/*/db/sql/` |
| 必须 | DDL 带表注释、列注释 |
| 必须 | 主键、索引有依据；UNIQUE 表达业务防重（赞、收藏） |
| 必须 | 禁止 `SELECT *` 进热路径（Mapper 只查需要列） |
| 必须 | 软删用状态位，查询默认排除已删 |
| 必须 | 单服务单库；跨库不要 join，用 Feign 或以后 MQ |
| 推荐 | 游标分页，不要深 offset |
| 推荐 | 字符集 utf8mb4；时间列与现网脚本一致 |
| 禁止 | 在循环里一条条查能改成 in / 一次 join 的（N+1） |

ONEPAGE：P1 社交表在 `onepage_content`（DEC-023）。`003_category.sql` 必须执行，缺 `category` 推荐流会 500。P2 关注表在 `onepage_user`，已有库须执行 `002_follow.sql`。P2 通知库 `onepage_notification`，须执行 `onepage-notification-service/db/sql/001_init.sql`；投递在事务提交后 Feign，失败不回滚赞藏关注。P2 私信库 `onepage_message`，须执行 `onepage-message-service/db/sql/001_init.sql`；本期 HTTP 文本 + 分享 `post_card`，不做 WS。获赞列表 Feign content JOIN `post_like`/`post_favorite`，不要新建表。P5 治理库 `onepage_moderation`，须执行 `onepage-moderation-service/db/sql/001_init.sql`；下架 Feign content，冻结 Feign auth，不要在 moderation 库改帖/账号。

---

## 6 ORM（MyBatis-Plus）

| 级别 | 约定 |
| --- | --- |
| 必须 | Mapper 接口继承 `BaseMapper`；复杂 SQL 写 XML，id 与方法名一致 |
| 必须 | `#{}` 参数；like 也要在参数里加 `%`，不要 `"%" + ${kw}` |
| 必须 | 实体字段与列映射清楚；布尔/枚举与库内值一致 |
| 必须 | 更新指定列，避免空字段把库覆盖成 null（按场景用 `UpdateWrapper` 或非空策略） |
| 推荐 | 批量操作用 MP 批 API 或一条 SQL，不要 for 里 insert |
| 推荐 | 分页用项目已有 cursor 工具，不要随手 `Page` 漏条件 |

---

## 7 工程结构

| 级别 | 约定 |
| --- | --- |
| 必须 | 只扩现有模块：`onepage-common` / `gateway` / `auth` / `user` / `content` / `notification` / `message` / `moderation`。新服务先改 [路线图](/engineering/tech/mvp-roadmap) |
| 必须 | 应用分层：`web` → `service` → `mapper`；`web` 不直接堆 SQL |
| 必须 | 依赖方向：service 可调 Feign `client`，不要循环依赖 |
| 必须 | 配置：`application.yml` 占位符 + `env.example` 键对齐 |
| 必须 | 网关路由与契约表一致；新 path 先改契约再改 gateway yml |
| 推荐 | 二方库不要随便升级 BOM 外版本 |
| 推荐 | 日志、Jackson、校验与 Boot 默认一致，不要每个服务复制一套 |

本地启动顺序与地址见 [技术架构](/engineering/tech/architecture)：user → auth → content → notification → message → moderation → gateway。

---

## 8 设计

| 级别 | 约定 |
| --- | --- |
| 必须 | 先改契约/表再写代码，避免「代码已发出去前端不知道」 |
| 必须 | 需要立即结果用 Feign（用户是否存在）；最终一致用 Outbox（发帖事件）。P1 Outbox **只落库不消费** |
| 必须 | 幂等：赞藏靠唯一约束或 `Idempotency-Key` |
| 推荐 | 一个 PR 只扩一条用户可感知能力 |
| 推荐 | 过早拆 social/message 进程否决（DEC-023）；表可以按目标 SQL 演进 |
| 禁止 | 在工作空间 Vue 里实现业务 API |

---

## 与阿里手册章节对照

| 官方手册常见分章 | 本文 |
| --- | --- |
| 编程规约（命名/常量/格式/OOP/日期/集合/并发/控制/注释/其他） | §1 |
| 异常日志 | §2 |
| 单元测试 | §3 |
| 安全规约 | §4 |
| MySQL | §5 |
| 工程结构 | §6–§7（含 ORM） |
| 设计规约 | §8 |

条款编号不以阿里 PDF 为准，以免与版权文本一一对应。查官方原句用团队持有的手册或 P3C 规则说明。

## 检查清单（改 Java 提交前）

- [ ] 分层、命名、`@PathVariable` 名字
- [ ] 无魔法值、无 `Executors` 无界池、无 `SELECT *`、无拼接 SQL
- [ ] 写接口校验 `X-User-Id`；内部接口校验内部 token
- [ ] 日志无密钥；异常进统一错误体
- [ ] 改表/改 API/改服务边界已回写 MD（知识空间为长期事实源）

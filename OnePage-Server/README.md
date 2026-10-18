# ONEPAGE Server

十个 Spring Boot 进程。`application.yml` 只写 `${变量}`，运行时从**进程环境变量**取值（本地与上线同一套机制）。

Maven 模块、Java 包、启动类同一段名字：`onepage-{name}-service` → `com.onepage.{name}` → `{Name}Application`（网关无 `-service`：`onepage-gateway` → `com.onepage.gateway.GatewayApplication`）。

## 本地 IDEA

1. 复制 `env.example` 为 `.env`，按本机改密码。
2. 十份运行配置，**只留 Maven 模块名**，不要再按 `*Application` 自动生成一份：
   `onepage-user-service`、`onepage-auth-service`、`onepage-content-service`、`onepage-notification-service`、`onepage-message-service`、`onepage-moderation-service`、`onepage-search-service`、`onepage-ai-service`、`onepage-live-service`、`onepage-gateway`。
3. 每个配置：Modify options → **Environment files**（或 EnvFile 插件）→ 选 `OnePage-Server/.env`。IDEA 把文件变成进程环境变量再启动，不是程序自己读文件。
4. 网关可最先起（只依赖 Nacos + Redis）。业务服务互调走 Feign，不经网关。**search 放在 user、content 之后**；想第一次就索引在播则 live 也先于 search。打开社区前，对应后端要已注册，否则 8080 会 503。

上线不带 `.env`，由编排写入同名环境变量。

端口：gateway 8080、auth 8081、user 8082、content 8083、notification 8084、message 8085、moderation 8086、search 8087、ai 8088、live 8089。

本机中间件：Nacos **3.2.4**（Java 17 standalone）、Redis **8.0**、MinIO `2025-02-07`、Elasticsearch **9.5.4**。业务进程 **Java 21**。Nacos 地址默认 `[::1]:8848`（`.env` 的 `NACOS_SERVER_ADDR`）。

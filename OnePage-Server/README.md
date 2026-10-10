# ONEPAGE Server

四个 Spring Boot 进程。`application.yml` 只写 `${变量}`，运行时从**进程环境变量**取值（本地与上线同一套机制）。

## 本地 IDEA

1. 复制 `env.example` 为 `.env`，按本机改密码。
2. 四个主类各一份运行配置：`UserApplication`、`AuthApplication`、`ContentApplication`、`GatewayApplication`。
3. 每个配置：Modify options → **Environment files**（或 EnvFile 插件）→ 选 `OnePage-Server/.env`。IDEA 把文件变成进程环境变量再启动，不是程序自己读文件。
4. 启动顺序：user → auth → content → gateway。

上线不带 `.env`，由编排写入同名环境变量。

端口：gateway 8080、auth 8081、user 8082、content 8083。

本机中间件：Nacos **3.2.4**（Java 17 standalone）、Redis **8.0**、MinIO `2025-02-07`。业务进程 **Java 21**。Nacos 地址默认 `[::1]:8848`（`.env` 的 `NACOS_SERVER_ADDR`）。

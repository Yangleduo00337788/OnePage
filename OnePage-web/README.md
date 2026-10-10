# ONEPAGE Web（P0）

P0 只有 `apps/shell`：首页、登录、注册、个人资料与头像/封面上传。

```bash
pnpm install
# 复制 apps/shell/.env.example 为 apps/shell/.env
pnpm dev
```

浏览器打开 `http://localhost:5173`。`/api` 代理目标来自环境变量 `VITE_DEV_PROXY_TARGET`。

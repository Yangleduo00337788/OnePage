# ONEPAGE Web

社区 `apps/shell`（5174）、创作者 `apps/app-creator`（5175）、运营 `apps/app-ops`（5176）。

```bash
pnpm install
pnpm dev          # 社区 + 创作者
pnpm dev:ops      # 运营后台 http://localhost:5176
```

`/api` 代理目标来自 `VITE_DEV_PROXY_TARGET`。运营站不挂社区导航。

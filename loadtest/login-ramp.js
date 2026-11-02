import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';

// 已有账号登录。不注册。阶梯 100 保持 3 分钟，再 200 保持 3 分钟。全程约 7 分 30 秒。
// P95 小于 15 秒：超过网关等待时间就会被掐掉。
export const options = {
  scenarios: {
    login: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '3m', target: 100 },
        { duration: '30s', target: 200 },
        { duration: '3m', target: 200 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{name:login}': ['p(95)<15000'],
  },
};

const base = __ENV.K6_BASE_URL || 'http://127.0.0.1:8080';

export function setup() {
  if (!__ENV.K6_ACCOUNT || !__ENV.K6_PASSWORD) {
    exec.test.abort('请设置环境变量 K6_ACCOUNT 和 K6_PASSWORD');
  }
}

export default function () {
  const res = http.post(
    `${base}/api/v1/auth/login`,
    JSON.stringify({ account: __ENV.K6_ACCOUNT, password: __ENV.K6_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
  );
  check(res, { 'login 200': (r) => r.status === 200 });
}

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';

// 发笔记。登录只在 setup 做一次。每档先爬 30 秒，再保持 3 分钟：100 → 250 → 500。全程约 11 分钟。
// 每一轮申请一张图的上传凭证，但不把文件 PUT 到对象存储。正式发图必须先完成上传，所以笔记用纯文本、仅自己可见。
// 60 秒那几条阈值只为让汇总按接口分开，不是性能指标。
export const options = {
  scenarios: {
    post: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '3m', target: 100 },
        { duration: '30s', target: 250 },
        { duration: '3m', target: 250 },
        { duration: '30s', target: 500 },
        { duration: '3m', target: 500 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{name:presign}': ['p(95)<60000'],
    'http_req_duration{name:publish}': ['p(95)<60000'],
  },
};

const base = __ENV.K6_BASE_URL || 'http://127.0.0.1:8080';
const jsonHeaders = { 'Content-Type': 'application/json' };

export function setup() {
  const account = __ENV.K6_ACCOUNT;
  const password = __ENV.K6_PASSWORD;
  if (!account || !password) {
    exec.test.abort('请设置环境变量 K6_ACCOUNT 和 K6_PASSWORD');
  }
  const login = http.post(
    `${base}/api/v1/auth/login`,
    JSON.stringify({ account, password }),
    { headers: jsonHeaders },
  );
  if (login.status !== 200) {
    exec.test.abort(`login failed: ${login.status}`);
  }
  const cookies = http.cookieJar().cookiesForURL(base);
  const session = cookies.ONEPAGE_SESSION && cookies.ONEPAGE_SESSION[0];
  if (!session) {
    exec.test.abort('login did not set ONEPAGE_SESSION');
  }
  const catalog = http.get(`${base}/api/v1/catalog`);
  if (catalog.status !== 200) {
    exec.test.abort(`catalog failed: ${catalog.status}`);
  }
  const categories = catalog.json('categories');
  const category = categories && categories[0] && categories[0].name;
  if (!category) {
    exec.test.abort('catalog has no category');
  }
  return { session, category };
}

export default function (data) {
  const jar = http.cookieJar();
  jar.set(base, 'ONEPAGE_SESSION', data.session);

  const presign = http.post(
    `${base}/api/v1/uploads/presign`,
    JSON.stringify({ mimeType: 'image/jpeg', sizeBytes: 1024, purpose: 'post_image' }),
    { headers: jsonHeaders, tags: { name: 'presign' } },
  );
  check(presign, { 'presign 200': (r) => r.status === 200 });

  const published = http.post(
    `${base}/api/v1/posts`,
    JSON.stringify({
      title: 'k6',
      body: `k6 load ${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
      postType: 'text',
      category: data.category,
      visibility: 'self',
    }),
    { headers: jsonHeaders, tags: { name: 'publish' } },
  );
  check(published, { 'publish 201': (r) => r.status === 201 });
}

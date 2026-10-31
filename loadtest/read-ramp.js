import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';

// 读接口阶梯加压。登录只在 setup 做一次，各 VU 复用同一张 ONEPAGE_SESSION。
// 每档先爬升 30 秒，再保持 3 分钟：20 → 50 → 100，最后 30 秒降到 0。全程约 11 分钟。
// 60 秒那几条阈值只为让汇总按接口分开，不是性能指标。
export const options = {
  scenarios: {
    read: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '3m', target: 20 },
        { duration: '30s', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '3m', target: 100 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{name:explore}': ['p(95)<60000'],
    'http_req_duration{name:detail}': ['p(95)<60000'],
    'http_req_duration{name:comments}': ['p(95)<60000'],
    'http_req_duration{name:profile}': ['p(95)<60000'],
  },
};

const base = __ENV.K6_BASE_URL || 'http://127.0.0.1:8080';

export function setup() {
  const account = __ENV.K6_ACCOUNT;
  const password = __ENV.K6_PASSWORD;
  if (!account || !password) {
    exec.test.abort('请设置环境变量 K6_ACCOUNT 和 K6_PASSWORD');
  }

  const login = http.post(
    `${base}/api/v1/auth/login`,
    JSON.stringify({ account, password }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (login.status !== 200) {
    exec.test.abort(`login failed: ${login.status}`);
  }

  const cookies = http.cookieJar().cookiesForURL(base);
  const session = cookies.ONEPAGE_SESSION && cookies.ONEPAGE_SESSION[0];
  if (!session) {
    exec.test.abort('login did not set ONEPAGE_SESSION');
  }
  return { session };
}

export default function (data) {
  const jar = http.cookieJar();
  jar.set(base, 'ONEPAGE_SESSION', data.session);

  const feed = http.get(`${base}/api/v1/feeds/explore?limit=20`, { tags: { name: 'explore' } });
  if (!check(feed, { 'explore 200': (r) => r.status === 200 })) {
    return;
  }

  const page = feed.json();
  const items = page && page.items;
  if (!items || items.length === 0) {
    check(feed, { 'explore has post': () => false });
    return;
  }

  const post = items[Math.floor(Math.random() * items.length)];
  const detail = http.get(`${base}/api/v1/posts/${post.postId}`, { tags: { name: 'detail' } });
  check(detail, { 'detail 200': (r) => r.status === 200 });

  const comments = http.get(`${base}/api/v1/posts/${post.postId}/comments`, { tags: { name: 'comments' } });
  check(comments, { 'comments 200': (r) => r.status === 200 });

  const pageId = post.author && post.author.pageId;
  if (!pageId) {
    check(feed, { 'profile 200': () => false });
    return;
  }
  const profile = http.get(`${base}/api/v1/users/${pageId}`, { tags: { name: 'profile' } });
  check(profile, { 'profile 200': (r) => r.status === 200 });
}

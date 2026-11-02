import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';

// 登录、注册和读路径一起加压。每档先爬 30 秒，再保持 3 分钟：100 → 250 → 500，最后 30 秒降到 0。全程约 11 分钟。
// 每一轮用新邮箱注册再登录，避免和已有账号撞车。60 秒那几条阈值只为让汇总按接口分开，不是性能指标。
export const options = {
  scenarios: {
    authRead: {
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
    'http_req_duration{name:register}': ['p(95)<60000'],
    'http_req_duration{name:login}': ['p(95)<60000'],
    'http_req_duration{name:explore}': ['p(95)<60000'],
    'http_req_duration{name:detail}': ['p(95)<60000'],
    'http_req_duration{name:comments}': ['p(95)<60000'],
    'http_req_duration{name:profile}': ['p(95)<60000'],
  },
};

const base = __ENV.K6_BASE_URL || 'http://127.0.0.1:8080';
const password = __ENV.K6_REGISTER_PASSWORD || 'K6load500a';
const runId = Date.now().toString(36);
const jsonHeaders = { headers: { 'Content-Type': 'application/json' } };

export default function () {
  const email = `k6${runId}v${exec.vu.idInTest}i${exec.scenario.iterationInTest}@lt.onepage.test`;
  const body = JSON.stringify({ email, password });

  const registered = http.post(`${base}/api/v1/auth/register`, body, {
    headers: jsonHeaders.headers,
    tags: { name: 'register' },
  });
  if (!check(registered, { 'register 201': (r) => r.status === 201 })) {
    return;
  }

  const loggedIn = http.post(`${base}/api/v1/auth/login`, JSON.stringify({ account: email, password }), {
    headers: jsonHeaders.headers,
    tags: { name: 'login' },
  });
  if (!check(loggedIn, { 'login 200': (r) => r.status === 200 })) {
    return;
  }

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

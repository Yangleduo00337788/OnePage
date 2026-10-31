import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';

// 第一轮冒烟：1 个并发跑通登录和发现流。账号密码只从环境变量读。
export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1'],
  },
};

const base = __ENV.K6_BASE_URL || 'http://127.0.0.1:8080';

export function setup() {
  const account = __ENV.K6_ACCOUNT;
  const password = __ENV.K6_PASSWORD;
  if (!account || !password) {
    exec.test.abort('请设置环境变量 K6_ACCOUNT 和 K6_PASSWORD');
  }
  return { account, password };
}

export default function (data) {
  const login = http.post(
    `${base}/api/v1/auth/login`,
    JSON.stringify({ account: data.account, password: data.password }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (!check(login, { 'login 200': (r) => r.status === 200 })) {
    fail(`login status ${login.status}`);
  }

  const feed = http.get(`${base}/api/v1/feeds/explore?limit=5`);
  if (!check(feed, { 'explore 200': (r) => r.status === 200 })) {
    fail(`explore status ${feed.status}`);
  }

  const page = feed.json();
  const first = page && page.items && page.items[0];
  if (!first || !first.postId) {
    fail('explore returned no post');
  }

  const detail = http.get(`${base}/api/v1/posts/${first.postId}`);
  check(detail, { 'detail 200': (r) => r.status === 200 });

  const comments = http.get(`${base}/api/v1/posts/${first.postId}/comments`);
  check(comments, { 'comments 200': (r) => r.status === 200 });

  const pageId = first.author && first.author.pageId;
  if (pageId) {
    const profile = http.get(`${base}/api/v1/users/${pageId}`);
    check(profile, { 'profile 200': (r) => r.status === 200 });
  }
}

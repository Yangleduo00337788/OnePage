import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';

// 点赞、收藏、评论。登录只在 setup 做一次。阶梯 20 保持 3 分钟，再 50 保持 3 分钟。全程约 7 分 30 秒。
// 同一个账号对同一篇笔记重复点赞不会再插入。所以按轮次交替：偶数轮点上，奇数轮取消，这样每次都写库。
// 评论每轮新写一条。笔记从发现流里取，都是公开笔记。
// 60 秒那几条阈值只为让汇总按接口分开，不是性能指标。
export const options = {
  scenarios: {
    react: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '3m', target: 20 },
        { duration: '30s', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{name:like}': ['p(95)<60000'],
    'http_req_duration{name:favorite}': ['p(95)<60000'],
    'http_req_duration{name:comment}': ['p(95)<60000'],
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
  const feed = http.get(`${base}/api/v1/feeds/explore?limit=20`);
  if (feed.status !== 200) {
    exec.test.abort(`explore failed: ${feed.status}`);
  }
  const items = feed.json('items') || [];
  const postIds = items.map((item) => item && item.postId).filter(Boolean);
  if (postIds.length === 0) {
    exec.test.abort('explore has no post');
  }
  return { session, postIds };
}

export default function (data) {
  const jar = http.cookieJar();
  jar.set(base, 'ONEPAGE_SESSION', data.session);
  const postId = data.postIds[exec.scenario.iterationInTest % data.postIds.length];
  const on = exec.scenario.iterationInTest % 2 === 0;

  const like = on
    ? http.post(`${base}/api/v1/reactions/posts/${postId}/like`, null, { tags: { name: 'like' } })
    : http.del(`${base}/api/v1/reactions/posts/${postId}/like`, null, { tags: { name: 'like' } });
  check(like, { 'like 200': (r) => r.status === 200 });

  const favorite = on
    ? http.post(`${base}/api/v1/reactions/posts/${postId}/favorite`, null, { tags: { name: 'favorite' } })
    : http.del(`${base}/api/v1/reactions/posts/${postId}/favorite`, null, { tags: { name: 'favorite' } });
  check(favorite, { 'favorite 200': (r) => r.status === 200 });

  const comment = http.post(
    `${base}/api/v1/posts/${postId}/comments`,
    JSON.stringify({ content: `k6 ${exec.vu.idInTest}-${exec.scenario.iterationInTest}` }),
    { headers: jsonHeaders, tags: { name: 'comment' } },
  );
  check(comment, { 'comment 201': (r) => r.status === 201 });
}

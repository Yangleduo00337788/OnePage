import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';

// 混合。30 秒爬到 1000，保持 3 分钟，再 30 秒降到 0。全程约 4 分钟。
// 大约八成刷发现流和详情，一成登录或搜索（各半），一成里再分评论、发一篇仅自己可见的文字、私信。
// 不推流，不进运营后台。60 秒阈值只为让汇总按接口分开。
export const options = {
  scenarios: {
    mix: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 1000 },
        { duration: '3m', target: 1000 },
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
    'http_req_duration{name:login}': ['p(95)<60000'],
    'http_req_duration{name:search}': ['p(95)<60000'],
    'http_req_duration{name:comment}': ['p(95)<60000'],
    'http_req_duration{name:publish}': ['p(95)<60000'],
    'http_req_duration{name:im-open}': ['p(95)<60000'],
    'http_req_duration{name:im-send}': ['p(95)<60000'],
  },
};

const base = __ENV.K6_BASE_URL || 'http://127.0.0.1:8080';
const jsonHeaders = { 'Content-Type': 'application/json' };
const peerPassword = __ENV.K6_REGISTER_PASSWORD || 'K6load500a';
const searchQ = encodeURIComponent(__ENV.K6_SEARCH_Q || '一');
const searchTypes = ['post', 'user', 'topic', 'live'];

function readSession(res) {
  const baked = res.cookies && res.cookies.ONEPAGE_SESSION;
  if (baked && baked.length && baked[0].value) {
    return baked[0].value;
  }
  const header = (res.headers && (res.headers['Set-Cookie'] || res.headers['set-cookie'])) || '';
  const matched = String(header).match(/ONEPAGE_SESSION=([^;,\s]+)/);
  if (matched) {
    return matched[1];
  }
  const stored = http.cookieJar().cookiesForURL(base).ONEPAGE_SESSION;
  if (stored && stored.length) {
    return stored[stored.length - 1];
  }
  return '';
}

function authed(session, name) {
  const params = {
    headers: jsonHeaders,
    cookies: {
      ONEPAGE_SESSION: { value: session, replace: true },
    },
  };
  if (name) {
    params.tags = { name };
  }
  return params;
}

export function setup() {
  const account = __ENV.K6_ACCOUNT;
  const password = __ENV.K6_PASSWORD;
  if (!account || !password) {
    exec.test.abort('请设置环境变量 K6_ACCOUNT 和 K6_PASSWORD');
  }
  const email = `k6mix${Date.now().toString(36)}@lt.onepage.test`;
  const registered = http.post(
    `${base}/api/v1/auth/register`,
    JSON.stringify({ email, password: peerPassword, nickname: 'k6mix' }),
    { headers: jsonHeaders },
  );
  if (registered.status !== 201) {
    exec.test.abort(`peer register failed: ${registered.status}`);
  }
  const peerUserId = String(registered.json('userId') || '');
  if (!peerUserId) {
    exec.test.abort('peer user id missing');
  }

  const jar = http.cookieJar();
  jar.delete(base, 'ONEPAGE_SESSION');
  jar.clear(base);
  const login = http.post(
    `${base}/api/v1/auth/login`,
    JSON.stringify({ account, password }),
    { headers: jsonHeaders },
  );
  if (login.status !== 200) {
    exec.test.abort(`login failed: ${login.status}`);
  }
  const session = readSession(login);
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

  const feed = http.get(`${base}/api/v1/feeds/explore?limit=20`);
  if (feed.status !== 200) {
    exec.test.abort(`explore failed: ${feed.status}`);
  }
  const items = feed.json('items') || [];
  const postIds = [];
  for (let i = 0; i < items.length; i += 1) {
    if (items[i] && items[i].postId) {
      postIds.push(String(items[i].postId));
    }
  }
  if (postIds.length === 0) {
    exec.test.abort('explore has no post');
  }
  return { session, password, category, postIds, peerUserId };
}

export default function (data) {
  const roll = Math.random();
  if (roll < 0.8) {
    browse(data);
  } else if (roll < 0.85) {
    login(data);
  } else if (roll < 0.9) {
    search();
  } else if (roll < 0.94) {
    comment(data);
  } else if (roll < 0.97) {
    publish(data);
  } else {
    im(data);
  }
}

function browse(data) {
  const feed = http.get(`${base}/api/v1/feeds/explore?limit=20`, authed(data.session, 'explore'));
  if (!check(feed, { 'explore 200': (r) => r.status === 200 })) {
    return;
  }
  const items = feed.json('items') || [];
  const picked = items.length ? items[Math.floor(Math.random() * items.length)] : null;
  const postId = picked && picked.postId ? picked.postId : data.postIds[0];
  const detail = http.get(`${base}/api/v1/posts/${postId}`, authed(data.session, 'detail'));
  check(detail, { 'detail 200': (r) => r.status === 200 });
}

function login(data) {
  const res = http.post(
    `${base}/api/v1/auth/login`,
    JSON.stringify({ account: __ENV.K6_ACCOUNT, password: data.password }),
    { headers: jsonHeaders, tags: { name: 'login' } },
  );
  check(res, { 'login 200': (r) => r.status === 200 });
}

function search() {
  const type = searchTypes[Math.floor(Math.random() * searchTypes.length)];
  const res = http.get(`${base}/api/v1/search?q=${searchQ}&type=${type}&limit=20`, {
    tags: { name: 'search' },
  });
  check(res, { 'search 200': (r) => r.status === 200 });
}

function comment(data) {
  const postId = data.postIds[exec.scenario.iterationInTest % data.postIds.length];
  const res = http.post(
    `${base}/api/v1/posts/${postId}/comments`,
    JSON.stringify({ content: `k6 mix ${exec.vu.idInTest}-${exec.scenario.iterationInTest}` }),
    authed(data.session, 'comment'),
  );
  check(res, { 'comment 201': (r) => r.status === 201 });
}

function publish(data) {
  const res = http.post(
    `${base}/api/v1/posts`,
    JSON.stringify({
      title: 'k6 mix',
      body: `k6 mix ${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
      postType: 'text',
      category: data.category,
      visibility: 'self',
    }),
    authed(data.session, 'publish'),
  );
  check(res, { 'publish 201': (r) => r.status === 201 });
}

function im(data) {
  const opened = http.post(
    `${base}/api/v1/conversations`,
    JSON.stringify({ peerUserId: data.peerUserId }),
    authed(data.session, 'im-open'),
  );
  if (!check(opened, { 'im open 200 or 201': (r) => r.status === 200 || r.status === 201 })) {
    return;
  }
  const conversationId = opened.json('conversationId');
  if (!conversationId) {
    return;
  }
  const sent = http.post(
    `${base}/api/v1/conversations/${conversationId}/messages`,
    JSON.stringify({
      msgType: 'text',
      content: { text: `k6 mix ${exec.vu.idInTest}-${exec.scenario.iterationInTest}` },
    }),
    authed(data.session, 'im-send'),
  );
  check(sent, { 'im send 201': (r) => r.status === 201 });
}

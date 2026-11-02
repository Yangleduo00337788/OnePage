import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';

// 私信。登录只在 setup 做一次，并注册一个对端。阶梯 20 保持 3 分钟，再 50 保持 3 分钟。全程约 7 分 30 秒。
// 每一轮对同一个对端建会话（已有则 200），再发一条文本。60 秒阈值只为让汇总按接口分开。
export const options = {
  scenarios: {
    im: {
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
    'http_req_duration{name:open}': ['p(95)<60000'],
    'http_req_duration{name:send}': ['p(95)<60000'],
  },
};

const base = __ENV.K6_BASE_URL || 'http://127.0.0.1:8080';
const jsonHeaders = { 'Content-Type': 'application/json' };
const peerPassword = __ENV.K6_REGISTER_PASSWORD || 'K6load500a';

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

function authedParams(session, name) {
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
  const email = `k6im${Date.now().toString(36)}@lt.onepage.test`;
  const registered = http.post(
    `${base}/api/v1/auth/register`,
    JSON.stringify({ email, password: peerPassword, nickname: 'k6peer' }),
    { headers: jsonHeaders },
  );
  if (registered.status !== 201) {
    exec.test.abort(`peer register failed: ${registered.status}`);
  }
  const peerUserId = String(registered.json('userId') || '');
  if (!peerUserId) {
    exec.test.abort('peer user id missing');
  }
  // 注册会留下对端的 Cookie。清掉后再登录，罐里就只剩本次登录写下的那张。
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
  const me = String(login.json('userId') || '');
  if (!me || peerUserId === me) {
    exec.test.abort('peer user id missing');
  }
  const probe = http.post(
    `${base}/api/v1/conversations`,
    JSON.stringify({ peerUserId }),
    authedParams(session),
  );
  if (probe.status !== 200 && probe.status !== 201) {
    exec.test.abort(`open failed: ${probe.status} ${String(probe.body).slice(0, 200)}`);
  }
  return { session, peerUserId };
}

export default function (data) {
  const opened = http.post(
    `${base}/api/v1/conversations`,
    JSON.stringify({ peerUserId: data.peerUserId }),
    authedParams(data.session, 'open'),
  );
  if (!check(opened, { 'open 200 or 201': (r) => r.status === 200 || r.status === 201 })) {
    return;
  }
  const conversationId = opened.json('conversationId');
  if (!conversationId) {
    check(opened, { 'open has id': () => false });
    return;
  }

  const sent = http.post(
    `${base}/api/v1/conversations/${conversationId}/messages`,
    JSON.stringify({
      msgType: 'text',
      content: { text: `k6 ${exec.vu.idInTest}-${exec.scenario.iterationInTest}` },
    }),
    authedParams(data.session, 'send'),
  );
  check(sent, { 'send 201': (r) => r.status === 201 });
}

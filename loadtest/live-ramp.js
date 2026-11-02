import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';

// 直播观众。阶梯 20 保持 3 分钟，再 50 保持 3 分钟。全程约 7 分 30 秒。
// setup 登录一次，没有在播就开一场，不推流。每一轮拉在播列表、进这一场、拉聊天。
// 三个接口走同一条网关路由。60 秒阈值只为让汇总按接口分开。
export const options = {
  scenarios: {
    live: {
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
    'http_req_duration{name:list}': ['p(95)<60000'],
    'http_req_duration{name:room}': ['p(95)<60000'],
    'http_req_duration{name:chat}': ['p(95)<60000'],
  },
};

const base = __ENV.K6_BASE_URL || 'http://127.0.0.1:8080';
const jsonHeaders = { 'Content-Type': 'application/json' };

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

function authed(session) {
  return {
    headers: jsonHeaders,
    cookies: {
      ONEPAGE_SESSION: { value: session, replace: true },
    },
  };
}

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
  const session = readSession(login);
  if (!session) {
    exec.test.abort('login did not set ONEPAGE_SESSION');
  }

  const mine = http.get(`${base}/api/v1/live/mine`, authed(session));
  let sessionId = '';
  if (mine.status === 200) {
    sessionId = String(mine.json('sessionId') || '');
  }
  if (!sessionId) {
    const room = http.post(
      `${base}/api/v1/live/rooms`,
      JSON.stringify({ title: 'k6 live', category: '', notice: '' }),
      authed(session),
    );
    if (room.status !== 201) {
      exec.test.abort(`create room failed: ${room.status} ${String(room.body).slice(0, 160)}`);
    }
    const roomId = room.json('roomId');
    const started = http.post(
      `${base}/api/v1/live/rooms/${roomId}/start`,
      null,
      authed(session),
    );
    if (started.status !== 200) {
      exec.test.abort(`start live failed: ${started.status} ${String(started.body).slice(0, 160)}`);
    }
    sessionId = String(started.json('sessionId') || '');
  }
  if (!sessionId) {
    exec.test.abort('live session id missing');
  }
  return { sessionId };
}

export default function (data) {
  const list = http.get(`${base}/api/v1/live/sessions`, { tags: { name: 'list' } });
  check(list, { 'list 200': (r) => r.status === 200 });

  const room = http.get(`${base}/api/v1/live/sessions/${data.sessionId}`, { tags: { name: 'room' } });
  check(room, {
    'room 200': (r) => r.status === 200 && String(r.json('sessionId') || '') === String(data.sessionId),
  });

  const chat = http.get(`${base}/api/v1/live/sessions/${data.sessionId}/messages`, { tags: { name: 'chat' } });
  check(chat, { 'chat 200': (r) => r.status === 200 });
}

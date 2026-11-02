import http from 'k6/http';
import { check } from 'k6';

// 搜索阶梯加压。公开接口，不登录。每档先爬 30 秒，再保持 3 分钟：100 → 250 → 500，最后 30 秒降到 0。全程约 11 分钟。
// 每一轮依次查笔记、用户、话题、在播。四次都打在网关同一条 search 路由上，共用本机每秒 2000 的额度。
// 60 秒那几条阈值只为让汇总按类型分开，不是性能指标。
export const options = {
  scenarios: {
    search: {
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
    'http_req_duration{name:search-post}': ['p(95)<60000'],
    'http_req_duration{name:search-user}': ['p(95)<60000'],
    'http_req_duration{name:search-topic}': ['p(95)<60000'],
    'http_req_duration{name:search-live}': ['p(95)<60000'],
  },
};

const base = __ENV.K6_BASE_URL || 'http://127.0.0.1:8080';
const q = encodeURIComponent(__ENV.K6_SEARCH_Q || '一');

export default function () {
  search('post', 'search-post');
  search('user', 'search-user');
  search('topic', 'search-topic');
  search('live', 'search-live');
}

function search(type, name) {
  const res = http.get(`${base}/api/v1/search?q=${q}&type=${type}&limit=20`, { tags: { name } });
  check(res, { [`${name} 200`]: (r) => r.status === 200 });
}

// 【役割】負荷テスト①：100人が同時に、一覧・検索・詳細を見て回る（k6 で動かす。使い方は perf/README.md）。
// 【なぜ必要か】非機能要件の「100人が同時に使っても、95% の API が 0.5秒以内に返る」「検索の 95% が 0.5秒以内」を確かめるため。
import http from 'k6/http'
import { check, sleep } from 'k6'

// __ENV は、動かすときに -e で渡す値（テストする場所の URL）
const BASE = __ENV.BASE_URL || 'http://localhost:8083'
const WORDS = ['Java', 'Python', 'デザイン', 'インフラ', 'データベース', 'React', 'Go', 'セキュリティ', 'AI', 'もくもく会']

export const options = {
  scenarios: {
    browse: {
      // constant-vus：決めた人数（VU）が、決めた時間、同時に動き続ける
      executor: 'constant-vus',
      vus: 100,
      duration: '2m',
    },
  },
  // thresholds：合格の条件。守れなければ、k6 は失敗として終わる
  thresholds: {
    'http_req_duration': ['p(95)<500'],
    'http_req_duration{kind:search}': ['p(95)<500'],
    'http_req_failed': ['rate<0.01'],
    'checks': ['rate>0.99'],
  },
}

// 【何のため】1人分の「見て回る」動き。終わると、また最初から繰り返す
export default function () {
  // Math.random()：0 以上 1 未満のでたらめな数
  const word = WORDS[Math.floor(Math.random() * WORDS.length)]
  const communityId = 1 + Math.floor(Math.random() * 1000)

  const top = http.get(`${BASE}/api/events`, { tags: { kind: 'list' } })
  check(top, { 'イベント一覧 200': (r) => r.status === 200 })
  sleep(1 + Math.random() * 2)

  const search = http.get(`${BASE}/api/events?q=${encodeURIComponent(word)}`, { tags: { kind: 'search' } })
  check(search, { 'イベント検索 200': (r) => r.status === 200 })
  sleep(1 + Math.random() * 2)

  const communities = http.get(`${BASE}/api/communities?q=${encodeURIComponent(word + ' 初心者歓迎')}`, { tags: { kind: 'search' } })
  check(communities, { 'コミュニティ検索 200': (r) => r.status === 200 })
  sleep(1 + Math.random() * 2)

  const detail = http.get(`${BASE}/api/communities/${communityId}`, { tags: { kind: 'detail' } })
  check(detail, { 'コミュニティ詳細 200': (r) => r.status === 200 })
  const events = http.get(`${BASE}/api/communities/${communityId}/events`, { tags: { kind: 'list' } })
  check(events, { 'コミュニティのイベント 200': (r) => r.status === 200 })
  const items = events.status === 200 ? events.json('items') : []
  sleep(1 + Math.random() * 2)

  if (items.length > 0) {
    const event = http.get(`${BASE}/api/communities/${communityId}/events/${items[0].id}`, { tags: { kind: 'detail' } })
    check(event, { 'イベント詳細 200': (r) => r.status === 200 })
    sleep(1 + Math.random() * 2)
  }
}

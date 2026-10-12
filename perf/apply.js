// 【役割】負荷テスト②：申込みが1秒間に50件、20秒続く（合計1,000件）。同じイベントに、たくさんの人が一斉に申し込む。
// 【なぜ必要か】非機能要件の「申込みが1秒間に50件来ても、95% の API が 0.5秒以内に返る」「定員を超えない」を確かめるため。
// 200人が、定員50人のイベント5つに、それぞれ申し込む（1つのイベントに200人。50人が申込み済み、150人がキャンセル待ちになるはず）。
import http from 'k6/http'
import exec from 'k6/execution'
import { check } from 'k6'

const BASE = __ENV.BASE_URL || 'http://localhost:8083'
const USERS = 200
const EVENTS = 5
const PASSWORD = __ENV.PERF_PASSWORD || 'perf-test-password'

export const options = {
  // setup（ログインの準備）は、時間がかかってもよい
  setupTimeout: '5m',
  scenarios: {
    apply: {
      // constant-arrival-rate：人数ではなく「1秒間に何件」を守って送り続ける
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '20s',
      preAllocatedVUs: 100,
      maxVUs: 200,
    },
  },
  thresholds: {
    'http_req_duration{kind:apply}': ['p(95)<500'],
    'http_req_failed{kind:apply}': ['rate<0.01'],
    'checks': ['rate>0.99'],
  },
}

// 【何のため】Cookie の Set-Cookie から、名前と値を取り出す
function cookieValue(res, name) {
  const c = res.cookies[name]
  return c && c.length > 0 ? c[0].value : null
}

// 【何のため】準備（最初に1回だけ）：200人がログインし、申し込む先のイベント（開催前、メンバー以外も自動で受け付け）を5つ選ぶ
export function setup() {
  // 本番の設定では、ログインの Cookie に Secure（HTTPS のときだけ送る）が付いているので、k6 の Cookie の入れ物は、
  // http の宛先（手元の箱）には保存しない。そのため、返事の Set-Cookie から直接取り出し、自分で Cookie のヘッダーを作る
  const sessions = []
  for (let i = 0; i < USERS; i++) {
    const email = `perf-${1001 + i}@example.com`
    const csrf = http.get(`${BASE}/api/auth/csrf`)
    const xsrf = cookieValue(csrf, 'XSRF-TOKEN')
    const login = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password: PASSWORD }), {
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf, Cookie: `XSRF-TOKEN=${xsrf}` },
    })
    if (login.status !== 200) throw new Error(`ログインに失敗：${email} ${login.status}`)
    const session = cookieValue(login, 'JSESSIONID')
    // ログインのあと、CSRF トークンを受け取り直す（Spring Security は、ログインのときに作り直す）
    const after = http.get(`${BASE}/api/auth/csrf`, { headers: { Cookie: `JSESSIONID=${session}` } })
    sessions.push({ session, xsrf: cookieValue(after, 'XSRF-TOKEN') || xsrf })
  }
  // 開催前のイベントから、コミュニティ 500番台のものを5つ（ほかのテストのデータと重ならないように）
  const events = []
  for (let c = 501; events.length < EVENTS; c++) {
    const list = http.get(`${BASE}/api/communities/${c}/events`).json('items')
    if (list.length > 0) events.push({ communityId: c, eventId: list[0].id })
  }
  return { sessions, events }
}

// 【何のため】1件の申込み。何件目か（iterationInTest）から、だれが、どのイベントに申し込むかを決める（同じ組み合わせにならない）
export default function (data) {
  const n = exec.scenario.iterationInTest
  if (n >= USERS * EVENTS) return
  const user = data.sessions[n % USERS]
  const target = data.events[Math.floor(n / USERS)]
  const res = http.post(
    `${BASE}/api/communities/${target.communityId}/events/${target.eventId}/applications`,
    null,
    {
      headers: { Cookie: `JSESSIONID=${user.session}; XSRF-TOKEN=${user.xsrf}`, 'X-XSRF-TOKEN': user.xsrf },
      tags: { kind: 'apply' },
    },
  )
  check(res, { '申込み 201': (r) => r.status === 201 })
}

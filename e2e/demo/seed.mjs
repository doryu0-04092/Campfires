/**
 * 【役割】デモ動画の録画用に、見本のデータ（利用者・コミュニティ・イベント・申込み）を API で入れるスクリプト。
 * 【なぜ必要か】動画のたびに同じデータから始められるようにするため。手元の、録画用の箱（空のデータベース）にだけ使う。
 * 本番には使わない（本番の利用者の目に触れるデータが増えるため）。使い方は e2e/demo/README.md。
 *
 * 入れるもの：
 * - 利用者：ゆうき（オーナー）、さくら・はると・あおい（Java 勉強会 東京のメンバー）
 * - コミュニティ：Java 勉強会 東京、React もくもく会、AWS 入門ハンズオン（どれも、ゆうきがオーナー）
 * - イベント：Spring Boot 入門ハンズオン（自動で承認）、Java 21 の新機能を読む会（メンバーは手動で承認）など
 */
// 録画用の箱の URL（DEMO_BASE_URL で変えられる）と、見本の利用者のパスワード（手元の録画用の箱だけで使う）
const BASE = process.env.DEMO_BASE_URL ?? 'http://localhost:8084'
const PASSWORD = 'video-demo-2026'
// 【何のため】1人分の、API とのやり取り（Cookie と CSRF トークンを覚えておく）を作る
async function session() {
  const jar = {}
  const call = async (method, path, body) => {
    const headers = { Cookie: Object.entries(jar).map(([k, v]) => `${k}=${v}`).join('; ') }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (method !== 'GET' && jar['XSRF-TOKEN']) headers['X-XSRF-TOKEN'] = jar['XSRF-TOKEN']
    const res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
    for (const c of res.headers.getSetCookie()) { const [kv] = c.split(';'); const i = kv.indexOf('='); jar[kv.slice(0, i)] = kv.slice(i + 1) }
    const t = await res.text()
    if (res.status >= 400) throw new Error(`${method} ${path} ${res.status} ${t}`)
    return t ? JSON.parse(t) : null
  }
  await call('GET', '/api/auth/csrf')
  return call
}
// 【何のため】利用者を登録し、ログインした状態のやり取りと、番号を返す
async function user(nick, email, real) {
  const call = await session()
  const me = await call('POST', '/api/auth/signup', { realName: real, nickname: nick, email, password: PASSWORD, agreedToPolicies: true })
  await call('GET', '/api/auth/csrf')
  return { call, id: me.id }
}
// 【何のため】今から days 日後の、日本時間の hour 時を、API が受け取る形（ISO 8601）で返す
const jst = (days, hour, min = 0) => { const d = new Date(Date.now() + days * 864e5); d.setUTCHours(hour - 9, min, 0, 0); return d.toISOString() }
const owner = await user('ゆうき', 'yuki@example.com', '佐藤 勇気')
const sakura = await user('さくら', 'sakura@example.com', '鈴木 さくら')
const haruto = await user('はると', 'haruto@example.com', '高橋 陽翔')
const aoi = await user('あおい', 'aoi@example.com', '田中 葵')
const java = await owner.call('POST', '/api/communities', { name: 'Java 勉強会 東京', description: '毎月、Java と Spring Boot を一緒に学ぶ勉強会です。初心者も歓迎します。' })
const react = await owner.call('POST', '/api/communities', { name: 'React もくもく会', description: '週末に集まって、それぞれ React のアプリを作る会です。' })
const aws = await owner.call('POST', '/api/communities', { name: 'AWS 入門ハンズオン', description: 'Terraform で AWS の環境を作りながら学びます。' })
for (const m of [sakura, haruto, aoi]) {
  const r = await m.call('POST', `/api/communities/${java.id}/join-requests`)
  await owner.call('PATCH', `/api/communities/${java.id}/join-requests/${r.id}`, { status: 'approved' })
}
const ev = (c, o) => owner.call('POST', `/api/communities/${c}/events`, { description: '', location: 'オンライン（Zoom）', capacity: 20, memberApproval: 'auto', nonMemberApproval: 'auto', ...o })
const spring = await ev(java.id, { title: 'Spring Boot 入門ハンズオン', description: 'Spring Boot で、はじめての API を作ります。', startAt: jst(7, 19), endAt: jst(7, 21), applicationDeadline: jst(6, 23), location: '東京・渋谷（会議室 A）', capacity: 20 })
const java21 = await ev(java.id, { title: 'Java 21 の新機能を読む会', description: 'record、パターンマッチ、仮想スレッドを読みます。', startAt: jst(14, 19), endAt: jst(14, 21), applicationDeadline: jst(13, 23), capacity: 10, memberApproval: 'manual' })
await ev(react.id, { title: 'React 19 もくもく会', startAt: jst(10, 13), endAt: jst(10, 17), applicationDeadline: jst(9, 23), capacity: 15 })
await ev(aws.id, { title: 'Terraform で ECS を作る', startAt: jst(21, 19), endAt: jst(21, 21), applicationDeadline: jst(20, 23), capacity: 12 })
await haruto.call('POST', `/api/communities/${java.id}/events/${spring.id}/applications`)
console.log('seeded', { java: java.id, spring: spring.id, java21: java21.id })

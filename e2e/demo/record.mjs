/**
 * 【役割】README に載せるデモ動画を録画するスクリプト。Edge を自動で操作し、画面を動画（webm）に残す。
 * 【なぜ必要か】主な機能の流れ（さくらが申し込み → オーナーのゆうきが承認 → イベントを作る）を、だれでも同じ手順で撮り直せるようにするため。
 *
 * 見ている人に分かりやすくするため、画面の上に、次のものを描く（アプリの画面そのものは変えない）：
 * - カーソル：押す場所まで動く。押した瞬間に、オレンジの波紋を出す
 * - オレンジの枠：これから押すボタンや、入力する欄を強調する（強調したものだけを押す。押さないものは強調しない）
 * - 字幕（画面の下）：今、だれが何をしているか
 * - 案内（画面全体）：さくら → ゆうき のように、操作する人が替わるとき
 * - 最後の説明の画面：画面の構造、今回操作した流れ、アプリの仕組み
 * 画面の移り変わりは、すべて画面を押して起こす（URL で直接開くのは、最初のトップの画面だけ）。
 *
 * 使い方は e2e/demo/README.md（seed.mjs でデータを入れてから動かす）。
 */
import { chromium } from '@playwright/test'

// 録画用の箱の URL と、動画を書き出すフォルダ
const BASE = process.env.DEMO_BASE_URL ?? 'http://localhost:8084'
const OUT = process.argv[2] ?? 'demo-output'
const PASSWORD = 'video-demo-2026'
// 待ち時間の倍率（大きくすると、ゆっくりになる）
const SPEED = 1.6
const pause = (ms) => new Promise((r) => setTimeout(r, ms * SPEED))

const browser = await chromium.launch({ channel: 'msedge', headless: true })
const context = await browser.newContext({
  viewport: { width: 1280, height: 720 },
  locale: 'ja-JP', timezoneId: 'Asia/Tokyo',
  recordVideo: { dir: OUT, size: { width: 1280, height: 720 } },
})
const page = await context.newPage()
const main = page.locator('main')
const header = page.locator('header')
const aside = page.locator('aside')

// ---------- 画面に描く道具（カーソル、波紋、強調、字幕、案内） ----------
let cursor = { x: 640, y: 360 }
let currentCaption = ''

// 道具の部品が、今の画面になければ作る（画面を読み込み直すと消えるため）
async function ensureOverlay() {
  await page.evaluate(([x, y, cap]) => {
    if (!document.getElementById('__cursor')) {
      const c = document.createElement('div')
      c.id = '__cursor'
      c.style.cssText = `position:fixed;left:${x}px;top:${y}px;width:28px;height:28px;z-index:100002;pointer-events:none;transition:left .55s ease, top .55s ease;filter:drop-shadow(0 2px 3px rgba(0,0,0,.45))`
      c.innerHTML = '<svg width="28" height="28" viewBox="0 0 24 24"><path d="M3 2 L3 19 L8 14 L11.5 21.5 L14.5 20 L11 12.8 L18 12.8 Z" fill="#fff" stroke="#111" stroke-width="1.5" stroke-linejoin="round"/></svg>'
      document.body.appendChild(c)
    }
    if (!document.getElementById('__caption') && cap) {
      const el = document.createElement('div')
      el.id = '__caption'
      el.style.cssText = 'position:fixed;left:50%;bottom:24px;transform:translateX(-50%);z-index:100001;background:rgba(32,26,22,.9);color:#fff;font-size:22px;font-weight:600;line-height:1.4;font-family:"Yu Gothic UI","Meiryo",sans-serif;padding:10px 24px;border-radius:10px;box-shadow:0 4px 16px rgba(0,0,0,.25);pointer-events:none;white-space:nowrap'
      el.textContent = cap
      document.body.appendChild(el)
    }
  }, [cursor.x, cursor.y, currentCaption])
}

// カーソルを、要素の真ん中まで動かし、要素を枠で強調する
async function pointTo(loc) {
  await loc.scrollIntoViewIfNeeded()
  const box = await loc.boundingBox()
  const x = box.x + Math.min(box.width / 2, 120)
  const y = box.y + box.height / 2
  await ensureOverlay()
  await page.evaluate(([x, y, bx, by, bw, bh]) => {
    const c = document.getElementById('__cursor')
    c.style.left = `${x}px`
    c.style.top = `${y}px`
    const h = document.createElement('div')
    h.className = '__hl'
    h.style.cssText = `position:fixed;left:${bx - 4}px;top:${by - 4}px;width:${bw + 8}px;height:${bh + 8}px;border:3px solid #f59f00;border-radius:8px;box-shadow:0 0 0 4px rgba(245,159,0,.25);z-index:100000;pointer-events:none`
    document.body.appendChild(h)
  }, [x, y, box.x, box.y, box.width, box.height])
  cursor = { x, y }
  await pause(450)
}

// 押した場所に、波紋を出し、強調の枠を消す
async function ripple() {
  await page.evaluate(([x, y]) => {
    const r = document.createElement('div')
    r.style.cssText = `position:fixed;left:${x - 22}px;top:${y - 22}px;width:44px;height:44px;border-radius:50%;background:rgba(245,159,0,.55);z-index:100001;pointer-events:none;transition:transform .45s ease, opacity .45s ease`
    document.body.appendChild(r)
    requestAnimationFrame(() => { r.style.transform = 'scale(1.8)'; r.style.opacity = '0' })
    setTimeout(() => r.remove(), 600)
    document.querySelectorAll('.__hl').forEach((e) => e.remove())
  }, [cursor.x, cursor.y])
}

// 押す：カーソルを動かして、強調して、波紋を出して、押す
async function press(loc) {
  await pointTo(loc)
  await ripple()
  await pause(150)
  await loc.click()
  await pause(500)
  await ensureOverlay()
}

// 入力する：欄を押してから、人が打つくらいの速さで入れる
async function typeInto(loc, text) {
  await pointTo(loc)
  await ripple()
  await loc.click()
  await loc.pressSequentially(text, { delay: 85 })
  await pause(250)
}

// 日時の欄などに、まとめて入れる（欄を指してから入れる）
async function fillInto(loc, value) {
  await pointTo(loc)
  await ripple()
  await loc.fill(value)
  await pause(300)
}

// 画面の下の字幕
async function caption(text) {
  currentCaption = text
  await ensureOverlay()
  await page.evaluate((t) => { document.getElementById('__caption').textContent = t }, text)
}

// 画面全体の案内
async function card(title, sub, ms = 2600) {
  await page.evaluate(([t, u]) => {
    const el = document.createElement('div')
    el.id = '__card'
    el.style.cssText = 'position:fixed;inset:0;z-index:100003;background:rgba(32,26,22,.86);color:#fff;display:flex;flex-direction:column;align-items:center;justify-content:center;font-family:"Yu Gothic UI","Meiryo",sans-serif;pointer-events:none'
    el.innerHTML = '<div style="font-size:44px;font-weight:700;line-height:1.4;text-align:center"></div><div style="font-size:26px;font-weight:500;line-height:1.6;margin-top:16px;opacity:.92;text-align:center"></div>'
    el.children[0].textContent = t
    el.children[1].textContent = u
    document.body.appendChild(el)
  }, [title, sub])
  await pause(ms)
  await page.evaluate(() => document.getElementById('__card')?.remove())
}

async function login(email) {
  await page.getByRole('heading', { name: 'ログイン' }).waitFor()
  await ensureOverlay()
  await typeInto(page.getByLabel('メールアドレス'), email)
  await typeInto(page.getByLabel('パスワード', { exact: true }), PASSWORD)
  await press(page.getByRole('button', { name: 'ログイン' }))
  await header.getByRole('button', { name: 'ログアウト' }).waitFor()
  await ensureOverlay()
  await pause(900)
}

// ---------- ① ログインしていない人：イベントを探す ----------
await page.goto(BASE + '/')
await ensureOverlay()
await card('Campfires', '勉強会・コミュニティ運営のためのイベント申込み管理アプリ', 2400)
await caption('さくら（まだログインしていない）：イベントを探す')
await pause(1000)
await typeInto(page.getByLabel('イベントを検索'), 'Java')
await press(page.getByRole('button', { name: '検索' }))
await pause(1800)
await caption('さくら（まだログインしていない）：ゆうきがオーナーのコミュニティと、そのイベントを見る')
await press(aside.getByRole('link', { name: 'コミュニティ一覧' }))
await pause(900)
await press(main.getByRole('link', { name: 'Java 勉強会 東京' }))
await pause(1500)
await press(main.getByRole('link', { name: 'Java 21 の新機能を読む会' }).first())
await pause(1800)

// ---------- ② メンバーがログインして、申し込む ----------
await card('さくらがログインして、申し込む', 'ゆうきが作った「Java 21 の新機能を読む会」（オーナーの承認が必要なイベント）', 3000)
await caption('さくら：「申し込む」を押すと、ログインの画面へ')
await pause(600)
await press(main.getByRole('link', { name: '申し込む' }))
await caption('さくら：ログインする')
await login('sakura@example.com')
await main.getByRole('button', { name: '申し込む', exact: true }).waitFor()
await ensureOverlay()
await caption('さくら：ログインのあと、元のイベントの画面に戻る')
await pause(1400)
await caption('さくら：イベントに申し込む')
await pause(500)
await press(page.getByRole('button', { name: '申し込む', exact: true }))
await main.getByText(/承認を待っています/).waitFor()
await ensureOverlay()
await caption('さくら：申込みが「承認待ち」になる（オーナーの承認を待つ）')
await pause(2200)
await caption('さくら：申し込んだイベントが「参加予定」に出る')
await press(aside.getByRole('link', { name: 'イベント一覧' }))
await pause(1800)
await caption('さくら：ログアウトする')
await press(header.getByRole('button', { name: 'ログアウト' }))
await pause(800)

// ---------- ③ オーナーがログインして、承認待ちの申込みを承認する ----------
await card('ここから、オーナー（ゆうき）として操作します', 'さくらの申込みを承認します', 3000)
await caption('ゆうき：ログインする')
await press(header.getByRole('link', { name: 'ログイン' }))
await login('yuki@example.com')
await caption('ゆうき：管理メニューから、自分のイベントの申込者一覧を開く')
await press(aside.getByRole('button', { name: /オーナー・運営スタッフ用/ }))
await pause(700)
await press(aside.getByRole('link', { name: '申込者一覧' }))
await pause(900)
await press(main.getByRole('link', { name: 'Java 21 の新機能を読む会' }))
await pause(1400)
await caption('ゆうき：さくらの申込みを承認する')
await press(main.locator('li', { hasText: 'さくら' }).getByRole('button', { name: '承認する' }))
await main.locator('li', { hasText: 'さくら' }).getByText('申込み済み').waitFor()
await ensureOverlay()
await caption('ゆうき：さくらが「申込み済み」になった')
await pause(2200)

// ---------- ④ オーナーが、新しいイベントを作る ----------
await caption('ゆうき：新しいイベントを作る')
await press(aside.getByRole('link', { name: 'イベント作成' }))
await pause(700)
await press(main.getByRole('link', { name: 'React もくもく会' }))
await page.getByRole('heading', { name: 'イベントを作る' }).waitFor()
await ensureOverlay()
await pause(500)
await typeInto(page.getByLabel('タイトル'), 'React Router 入門もくもく会')
await typeInto(page.getByLabel('説明'), 'React Router で、画面の切り替えを作ってみます。')
const d = new Date(Date.now() + 17 * 864e5 + 9 * 3600e3)
const ymd = (t) => `${t.getUTCFullYear()}-${String(t.getUTCMonth() + 1).padStart(2, '0')}-${String(t.getUTCDate()).padStart(2, '0')}`
await fillInto(page.getByLabel('開始日時'), `${ymd(d)}T13:00`)
await fillInto(page.getByLabel('終了日時'), `${ymd(d)}T17:00`)
await fillInto(page.getByLabel('申込み締め切り日時'), `${ymd(new Date(d.getTime() - 864e5))}T23:00`)
await typeInto(page.getByLabel('場所'), 'オンライン（Discord）')
await page.getByLabel('定員').fill('')
await typeInto(page.getByLabel('定員'), '15')
await press(page.getByRole('button', { name: '作成する' }))
await page.getByRole('heading', { name: 'React Router 入門もくもく会', level: 1 }).waitFor()
await ensureOverlay()
await caption('ゆうき：イベントができた')
await pause(2200)

// ---------- ⑤ 最後に、アプリの全体の説明 ----------
await page.evaluate(() => {
  const el = document.createElement('div')
  el.style.cssText = 'position:fixed;inset:0;z-index:100010;background:#fbf7f3;color:#2b2521;font-family:"Yu Gothic UI","Meiryo",sans-serif;padding:34px 40px;box-sizing:border-box'
  const box = (title, body, color) => `<div style="background:#fff;border:2px solid ${color};border-radius:14px;padding:16px 18px;box-shadow:0 2px 8px rgba(0,0,0,.06)"><div style="font-size:21px;font-weight:700;color:${color};margin-bottom:10px">${title}</div>${body}</div>`
  const item = (t, s = '') => `<div style="border:1px solid #e3d7cc;border-radius:8px;padding:7px 10px;margin:6px 0;font-size:15px;line-height:1.45"><b>${t}</b>${s ? `<div style="font-size:13px;color:#6b625b">${s}</div>` : ''}</div>`
  const arrow = '<div style="text-align:center;font-size:18px;color:#b03a0b;line-height:1">▼</div>'
  el.innerHTML = `
    <div style="display:flex;align-items:baseline;gap:18px;margin-bottom:18px">
      <div style="font-size:34px;font-weight:800;color:#b03a0b">Campfires</div>
      <div style="font-size:18px;color:#6b625b">勉強会・コミュニティ運営のためのイベント申込み管理アプリ</div>
    </div>
    <div style="display:grid;grid-template-columns:1fr 1.05fr 1fr;gap:20px">
      ${box('画面の構造', `
        <div style="border:1px solid #e3d7cc;border-radius:8px;overflow:hidden;font-size:13px">
          <div style="background:#f4ebe3;padding:6px 8px">上のメニュー：ログイン／お知らせ／アカウント設定</div>
          <div style="display:grid;grid-template-columns:44% 56%">
            <div style="background:#faf3ec;padding:8px;border-right:1px solid #e3d7cc;line-height:1.6">
              <b>左のメニュー</b><br>利用者用<br>・イベント一覧<br>・コミュニティ一覧<br><br><b>オーナー・運営スタッフ用</b><br>・コミュニティの管理<br>・メンバー管理<br>・イベントの作成・編集<br>・申込者一覧（承認）
            </div>
            <div style="padding:8px;line-height:1.6"><b>真ん中の画面</b><br>一覧・検索<br>コミュニティの詳細<br>イベントの詳細・申込み<br>作成・編集のフォーム</div>
          </div>
        </div>`, '#b03a0b')}
      ${box('今回操作した流れ', `
        ${item('さくら（ログイン前）', 'ゆうきのイベントを見つける（検索 → コミュニティ → イベント）')}${arrow}
        ${item('さくら（メンバー）', '申し込む → ログイン → 元の画面で申し込む → 承認待ち')}${arrow}
        ${item('ゆうき（オーナー）', '申込者一覧で、さくらを承認 → さくらは申込み済み')}${arrow}
        ${item('ゆうき（オーナー）', '新しいイベントを作る（定員・承認の方法を設定）')}`, '#2f6f4f')}
      ${box('アプリの仕組み', `
        ${item('ブラウザ', 'React（TypeScript）')}${arrow}
        ${item('ロードバランサー（HTTPS）', 'AWS ECS Express Mode')}${arrow}
        ${item('アプリ', 'Java 21・Spring Boot（ECS Fargate）')}${arrow}
        ${item('データベース', 'PostgreSQL 17（Amazon RDS）')}
        <div style="font-size:13px;color:#6b625b;margin-top:8px;line-height:1.5">Terraform で構築／GitHub Actions で CI/CD<br>CloudWatch・X-Ray で見張る</div>`, '#1f5f8b')}
    </div>`
  document.body.appendChild(el)
})
await pause(6500)

await context.close()
await browser.close()
console.log('recorded')

/**
 * 【役割】ログインしていない人が見る画面と、ログインが必要な画面の守りを、本物のブラウザで確かめるテスト。
 * 【なぜ必要か】ログインしていない人にも、コミュニティやイベントを見てもらい、参加してもらう入り口になる画面だから。
 * また、ログインが必要な画面に、ログインしていない人が入れないことも確かめる。
 *
 * 確かめる主な機能：
 * - トップ（イベント一覧・検索）
 * - コミュニティ一覧・検索、コミュニティ詳細、イベント詳細（ログインしていなくても見られる）
 * - プライバシーポリシー、利用規約、ページが見つからない画面
 * - ログインが必要な画面（アカウント設定、管理の画面）は、ログインの画面へ移動する
 * - 画面（HTML）の返事に、安全のためのヘッダー（CSP など）が付いている
 * （ログインしたあとに元の画面へ戻ることは、02-account.spec.ts で確かめる。ログインの回数を増やさないため）
 */
import { expect, test } from '@playwright/test'

import { account, runId, sessionOf } from '../../support/accounts'
import { createCommunityViaApi, createEventViaApi, header, main } from './helpers'

// このファイルのテストは、ログインしていない状態（Cookie なし）で行う。
// test.use は「このファイルのテストで使う設定」を決める書き方
test.use({ storageState: { cookies: [], origins: [] } })

// テストで使うデータの名前（実行ごとの番号を付けて、ほかのデータと区別する）。値は beforeAll で入れる
// （ファイルを読み込んだ時点では、まだ準備（global-setup）が終わっていないことがあるため）
let communityName: string
let eventTitle: string
// 準備で作ったデータの番号。let は「あとから値を入れる変数」
let communityId: number
let eventId: number

// 【何のため】ファイルの最初に1回だけ、オーナーとして、コミュニティとイベントを API で作っておく
test.beforeAll(async () => {
  communityName = `E2E 公開コミュニティ ${runId()}`
  eventTitle = `E2E 公開イベント ${runId()}`
  const owner = await sessionOf('owner')
  try {
    communityId = await createCommunityViaApi(owner, communityName, 'だれでも見られるコミュニティの説明です。')
    eventId = await createEventViaApi(owner, communityId, { title: eventTitle, location: 'E2E 公民館', capacity: 7 })
  } finally {
    await owner.dispose()
  }
})

test('トップの画面で、開催前のイベントを検索して見つけられる', async ({ page }) => {
  // 【何のため】アプリの入り口で、ログインしていない人がイベントを探せることを確かめる
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'イベントを探す' })).toBeVisible()
  // ログインしていないので、上部に「ログイン」「アカウント登録」が出る
  await expect(header(page).getByRole('link', { name: 'ログイン' })).toBeVisible()
  await expect(header(page).getByRole('link', { name: 'アカウント登録' })).toBeVisible()
  await expect(main(page).getByText(/開催前のイベント：\d+件/)).toBeVisible()

  // 実行ごとの番号で検索すると、作ったイベントが見つかる
  await page.getByLabel('イベントを検索').fill(eventTitle)
  await page.getByRole('button', { name: '検索' }).click()
  // 検索の文字は URL に入る（toHaveURL は、URL がそうなるまで待って確かめる）
  await expect(page).toHaveURL(/\?q=/)
  // /〜/ は「この形の文字」という書き方（正規表現）。\d+ は「1けた以上の数字」
  await expect(main(page).getByText(/検索結果：\d+件見つかりました/)).toBeVisible()
  await expect(main(page).getByRole('link', { name: eventTitle })).toBeVisible()
})

test('コミュニティ一覧で検索して、コミュニティ詳細を開ける', async ({ page }) => {
  // 【何のため】ログインしていない人が、コミュニティを探して、中身（説明・オーナー・イベント）を見られることを確かめる
  await page.goto('/communities')
  await expect(page.getByRole('heading', { name: 'コミュニティ一覧' })).toBeVisible()
  await page.getByLabel('コミュニティを検索').fill(communityName)
  await page.getByRole('button', { name: '検索' }).click()
  await expect(main(page).getByText(/検索結果：\d+件見つかりました/)).toBeVisible()

  await main(page).getByRole('link', { name: communityName }).click()
  await expect(page).toHaveURL(`/communities/${communityId}`)
  await expect(page.getByRole('heading', { name: communityName, level: 1 })).toBeVisible()
  await expect(main(page).getByText('だれでも見られるコミュニティの説明です。')).toBeVisible()
  await expect(main(page).getByText(`オーナー：${account('owner').nickname}`)).toBeVisible()
  // ログインしていないので、参加の申請の代わりに、ログインへの案内が出る
  await expect(main(page).getByText('参加を申請するには、')).toBeVisible()
  await expect(main(page).getByRole('button', { name: '参加を申請する' })).toHaveCount(0)
  // 管理の画面へのリンクは出ない
  await expect(main(page).getByRole('link', { name: 'メンバー管理' })).toHaveCount(0)
  // このコミュニティの、開催前のイベントが並ぶ
  await expect(main(page).getByRole('tab', { name: '開催前' })).toHaveAttribute('aria-selected', 'true')
  await expect(main(page).getByRole('link', { name: eventTitle })).toBeVisible()
})

test('ログインしていなくても、イベント詳細を見られる', async ({ page }) => {
  // 【何のため】イベントの情報は誰でも見られる決まり（ログインしていない人が、申し込む前に内容を確かめられる）
  await page.goto(`/communities/${communityId}/events/${eventId}`)
  await expect(page.getByRole('heading', { name: eventTitle, level: 1 })).toBeVisible()
  await expect(main(page).getByRole('link', { name: communityName })).toBeVisible()
  await expect(main(page).getByText('E2E 公民館')).toBeVisible()
  await expect(main(page).getByText('7人（残り 7人）')).toBeVisible()
  // 申し込むボタンは出ず、ログインへの案内が出る
  await expect(main(page).getByText('申し込むには、')).toBeVisible()
  await expect(main(page).getByRole('button', { name: '申し込む' })).toHaveCount(0)
})

test('画面下部のリンクから、プライバシーポリシーと利用規約を開ける', async ({ page }) => {
  // 【何のため】登録の前に、誰でも決まりを読めることを確かめる
  await page.goto('/')
  await page.locator('footer').getByRole('link', { name: 'プライバシーポリシー' }).click()
  await expect(page).toHaveURL('/privacy')
  await expect(page.getByRole('heading', { name: 'プライバシーポリシー', level: 1 })).toBeVisible()

  await page.locator('footer').getByRole('link', { name: '利用規約' }).click()
  await expect(page).toHaveURL('/terms')
  await expect(page.getByRole('heading', { name: '利用規約', level: 1 })).toBeVisible()
})

test('ない URL を開くと「ページが見つかりません」が出て、トップへ戻れる', async ({ page }) => {
  // 【何のため】URL を打ち間違えたときに、真っ白な画面ではなく、案内が出ることを確かめる
  await page.goto('/no-such-page')
  await expect(page.getByRole('heading', { name: 'ページが見つかりません' })).toBeVisible()
  await main(page).getByRole('link', { name: 'トップへ戻る' }).click()
  await expect(page).toHaveURL('/')
  await expect(page.getByRole('heading', { name: 'イベントを探す' })).toBeVisible()
})

test('ない番号のコミュニティを開くと「見つかりませんでした」が出る', async ({ page }) => {
  // 【何のため】削除された・存在しないデータを開いたときに、分かりやすい文が出ることを確かめる
  await page.goto('/communities/999999999')
  await expect(main(page).getByRole('alert')).toHaveText(/見つかりませんでした/)
})

// 「ログインが必要な画面」の例。どれを開いても、ログインの画面へ移動するはず
for (const path of ['/account', '/notifications', '/manage/communities', '/manage/communities/new', '/manage/select/members']) {
  test(`ログインしていないと ${path} は開けず、ログインの画面へ移動する`, async ({ page }) => {
    // 【何のため】ログインが必要な画面を、ログインしていない人に見せないことを確かめる
    await page.goto(path)
    await expect(page).toHaveURL('/login')
    await expect(page.getByRole('heading', { name: 'ログイン', level: 1 })).toBeVisible()
  })
}

test('画面の返事に、安全のためのヘッダーが付いている', async ({ page }) => {
  // 【何のため】ブラウザに「よそのプログラムを動かさない」「よその画面に埋め込ませない」などを指示できているかを確かめる
  const response = await page.goto('/')
  // response が null（返事がない）ことはないはず。! は「null ではない」と TypeScript に伝える書き方
  const headers = response!.headers()
  // CSP（Content-Security-Policy）：このサイトのプログラムだけを動かす決まり
  expect(headers['content-security-policy']).toContain("script-src 'self'")
  // nosniff：ファイルの種類を、ブラウザが勝手に推測しないようにする
  expect(headers['x-content-type-options']).toBe('nosniff')
  // ほかのサイトの画面に、枠（iframe）で埋め込まれないようにする（クリックをだます攻撃への対策）
  const denyFraming =
    headers['x-frame-options'] === 'DENY' || /frame-ancestors 'none'/.test(headers['content-security-policy'] ?? '')
  expect(denyFraming).toBe(true)
})

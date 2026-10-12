/**
 * 【役割】アカウントの機能（登録・ログアウト・ログイン・ニックネームの変更・アカウントの削除）を、画面から確かめるテスト。
 * 【なぜ必要か】利用者が最初に使う機能で、壊れると誰もアプリを使えなくなるため。
 *
 * 確かめる主な機能：
 * - アカウント登録（同意しないと押せない → 登録するとログインした状態になる）
 * - ログアウト
 * - パスワードが違うとき、エラーが出る
 * - ログインしていない人がイベントの「申し込む」を押すと、ログインのあと、そのイベントの画面に戻る
 * - 上の「ログイン」を押すと、ログインのあと、元の画面（検索の言葉も）に戻る
 * - ログインが必要な画面から来た人は、ログインのあと、元の画面に戻る
 * - アカウント設定で、ニックネームを変える
 * - アカウント設定で、パスワードを変える（変えた後も、この端末はログインしたまま）
 * - アカウントの削除（パスワードで確認してから削除し、ログインしていない状態に戻る）
 *
 * 本番の回数制限（登録は1時間に10回、ログインは10分に20回）があるので、
 * このファイルで、新しい利用者を1人（extra）だけ登録し、最後に、その利用者を削除する。
 * 前のテストの結果（登録した利用者）を、次のテストで使うので、順番に実行する（serial）。
 *
 * このファイルは、Edge（browser プロジェクト）だけで行い、Firefox・WebKit では行わない（playwright.config.ts の testIgnore）。
 * 3つのブラウザで行うと、登録とログインの回数が増え、本番の回数制限に収まらなくなるため（e2e/README.md の「回数制限の内訳」）。
 */
import { expect, test, type BrowserContext, type Page } from '@playwright/test'

import { forget, newAccountData, remember, sessionOf, tag } from '../../support/accounts'
import { createCommunityViaApi, createEventViaApi, header, main } from './helpers'

// serial：このファイルのテストを、書いた順に1つずつ行う。1つ失敗したら、残りは行わない
test.describe.configure({ mode: 'serial' })

// 登録する利用者のメールアドレスとパスワード（実行ごとに変わる）
// 値は beforeAll で入れる（ファイルを読み込んだ時点では、まだ準備（global-setup）が終わっていないことがあるため）
// ReturnType<typeof 関数> は「その関数が返す値の型」という意味
let extra: ReturnType<typeof newAccountData>
// 変更したあとのニックネーム（30文字まで）
const newNickname = `e2e-変更後-${Date.now().toString(36)}`
const secondNickname = `e2e-再変更-${Date.now().toString(36)}`
// 今のニックネーム（テストの途中で変わる）
let currentNickname: string

// このファイルのテストで、同じブラウザ（Cookie）を使い続ける。登録 → ログアウト → ログイン を続けて確かめるため
let context: BrowserContext
let page: Page

// 「申し込む」を押してログインする流れで使う、イベント（owner が API で作る。メンバー以外も申し込める）
let communityId: number
let eventId: number
let eventTitle: string

// 【何のため】ファイルの最初に、ログインしていないブラウザを1つ開く
test.beforeAll(async ({ browser }) => {
  extra = newAccountData('extra', 'ui')
  currentNickname = extra.nickname
  const owner = await sessionOf('owner')
  eventTitle = `E2E ログインして戻る ${tag()}`
  communityId = await createCommunityViaApi(owner, `E2E 戻り先のコミュニティ ${tag()}`)
  eventId = await createEventViaApi(owner, communityId, { title: eventTitle, nonMemberApproval: 'auto' })
  await owner.dispose()
  context = await browser.newContext()
  page = await context.newPage()
})

// 【何のため】ファイルの最後に、ブラウザを閉じる
test.afterAll(async () => {
  await context.close()
})

test('アカウント登録の画面から登録すると、ログインした状態になる', async () => {
  // 【何のため】新しい利用者が、画面から登録できることを確かめる
  await page.goto('/')
  await header(page).getByRole('link', { name: 'アカウント登録' }).click()
  await expect(page.getByRole('heading', { name: 'アカウント登録' })).toBeVisible()

  await page.getByLabel('本名').fill(extra.realName)
  await page.getByLabel('ニックネーム').fill(extra.nickname)
  await page.getByLabel('メールアドレス').fill(extra.email)
  // exact: true は「名前がぴったり同じもの」。「パスワードを表示する」のチェックボックスと区別するため
  await page.getByLabel('パスワード', { exact: true }).fill(extra.password)

  // 同意のチェックを入れるまでは、登録のボタンを押せない
  const submit = page.getByRole('button', { name: '登録する' })
  await expect(submit).toBeDisabled()
  await page.getByRole('checkbox', { name: /に同意します/ }).check()
  await expect(submit).toBeEnabled()
  await submit.click()

  // 登録に成功すると、トップの画面に移り、上部にニックネームが出る
  await expect(page).toHaveURL('/')
  await expect(header(page).getByText(`${extra.nickname} さん`)).toBeVisible()
  await expect(main(page).getByRole('heading', { name: '参加予定のイベント' })).toBeVisible()

  // 片付け（global-teardown）のために、登録した利用者を覚えておく。
  // page.request は、このブラウザの Cookie を使って API を呼ぶ（ログインした本人として、自分の番号を読む）
  const me = await (await page.request.get('/api/me')).json()
  remember({ ...extra, id: me.id })
})

test('ログアウトすると、ログインしていない状態に戻る', async () => {
  // 【何のため】共有のパソコンなどで、ログアウトしたあとに、ほかの人が続けて使えないことを確かめる。
  // ログインが必要な画面（アカウント設定）からログアウトしても、ログインの画面ではなく、トップの画面に移ることも確かめる
  // （以前は、ログイン状態が先に変わり、RequireAuth がログインの画面へ移動させるおそれがあった。AuthProvider の becomeGuest で直した）
  await page.goto('/account')
  await expect(page.getByRole('heading', { name: 'アカウント設定' })).toBeVisible()
  await header(page).getByRole('button', { name: 'ログアウト' }).click()
  await expect(page).toHaveURL('/')
  await expect(header(page).getByRole('link', { name: 'ログイン' })).toBeVisible()
  await expect(header(page).getByText(`${extra.nickname} さん`)).toHaveCount(0)
})

test('パスワードが違うと、ログインできず、エラーが出る', async () => {
  // 【何のため】間違ったパスワードでは、ログインできないことを確かめる
  await page.goto('/login')
  await page.getByLabel('メールアドレス').fill(extra.email)
  await page.getByLabel('パスワード', { exact: true }).fill(`${extra.password}-wrong`)
  await page.getByRole('button', { name: 'ログイン' }).click()
  // role="alert" の場所に、エラーの文が出る
  await expect(main(page).getByRole('alert')).toBeVisible()
  await expect(page).toHaveURL('/login')
  await expect(header(page).getByRole('link', { name: 'ログイン' })).toBeVisible()
})

/** 【何のため】ログインの画面で、extra のメールアドレスとパスワードを入れてログインする */
async function loginOnPage() {
  await expect(page).toHaveURL('/login')
  await page.getByLabel('メールアドレス').fill(extra.email)
  await page.getByLabel('パスワード', { exact: true }).fill(extra.password)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(header(page).getByText(`${currentNickname} さん`)).toBeVisible()
}

test('ログインしていない人が「申し込む」を押すと、ログインのあと、そのイベントの画面に戻る', async () => {
  // 【何のため】イベントを見つけた人が、ログインのあとに探し直さなくても、そのまま申し込めることを確かめる
  await page.goto(`/communities/${communityId}/events/${eventId}`)
  await expect(main(page).getByText('申し込むには、ログインが必要です。')).toBeVisible()
  // ログインしていないときの「申し込む」は、ログインの画面へのリンク
  await main(page).getByRole('link', { name: '申し込む' }).click()
  await loginOnPage()
  await expect(page).toHaveURL(`/communities/${communityId}/events/${eventId}`)
  // 戻ってきた画面では、本当に申し込めるボタンになっている
  await expect(main(page).getByRole('button', { name: '申し込む', exact: true })).toBeVisible()

  await header(page).getByRole('button', { name: 'ログアウト' }).click()
  await expect(header(page).getByRole('link', { name: 'ログイン' })).toBeVisible()
})

test('上の「ログイン」を押すと、ログインのあと、元の画面（検索の言葉も）に戻る', async () => {
  // 【何のため】検索の結果を見ている途中でログインしても、同じ検索の結果に戻れることを確かめる
  const query = encodeURIComponent(eventTitle)
  await page.goto(`/?q=${query}`)
  await expect(main(page).getByRole('link', { name: eventTitle })).toBeVisible()
  await header(page).getByRole('link', { name: 'ログイン' }).click()
  await loginOnPage()
  // toHaveURL に関数を渡すと、URL を受け取って「正しいか」を確かめられる。トップ（/）で、検索の言葉（q）がそのまま残っている
  await expect(page).toHaveURL((url) => url.pathname === '/' && url.searchParams.get('q') === eventTitle)
  await expect(main(page).getByRole('link', { name: eventTitle })).toBeVisible()

  await header(page).getByRole('button', { name: 'ログアウト' }).click()
  await expect(header(page).getByRole('link', { name: 'ログイン' })).toBeVisible()
})

test('ログインが必要な画面から来た人は、ログインのあと、元の画面に戻る', async () => {
  // 【何のため】ログインのあとに、開こうとしていた画面（ここではアカウント設定）へ戻れることを確かめる
  await page.goto('/account')
  await expect(page).toHaveURL('/login')

  await page.getByLabel('メールアドレス').fill(extra.email)
  await page.getByLabel('パスワード', { exact: true }).fill(extra.password)
  await page.getByRole('button', { name: 'ログイン' }).click()

  await expect(page).toHaveURL('/account')
  await expect(page.getByRole('heading', { name: 'アカウント設定' })).toBeVisible()
  await expect(header(page).getByText(`${extra.nickname} さん`)).toBeVisible()
  // 本人の画面なので、本名とメールアドレスが見える
  await expect(main(page).getByText(extra.realName)).toBeVisible()
  await expect(main(page).getByText(extra.email)).toBeVisible()
})

test('アカウント設定で、ニックネームを変えられる', async () => {
  // 【何のため】ニックネームを変えると、画面上部の表示も新しいニックネームになることを確かめる
  const field = page.getByLabel('新しいニックネーム')
  // exact: true：名前がぴったり「変更する」のボタン（「パスワードを変更する」のボタンと区別するため）
  const submit = page.getByRole('button', { name: '変更する', exact: true })
  // 今と同じニックネームのときは、押せない
  await expect(field).toHaveValue(extra.nickname)
  await expect(submit).toBeDisabled()

  await field.fill(newNickname)
  await submit.click()
  await expect(header(page).getByText(`${newNickname} さん`)).toBeVisible()
  // 「登録している情報」の欄も、新しいニックネームになる
  await expect(main(page).getByText(newNickname, { exact: true })).toBeVisible()
  currentNickname = newNickname

  // 画面を読み込み直しても、新しいニックネームのまま（サーバーに保存されている）
  await page.reload()
  await expect(header(page).getByText(`${newNickname} さん`)).toBeVisible()
})

test('ニックネームを変えると「ニックネームを変更しました。」と出る', async () => {
  // 【何のため】変更できたことが、利用者に伝わることを確かめる。
  // （以前は、変更に成功した瞬間にフォームが作り直され、この文が消えていた。AccountSettingsPage.tsx で直した）
  await page.getByLabel('新しいニックネーム').fill(secondNickname)
  await page.getByRole('button', { name: '変更する', exact: true }).click()
  // 変更そのものは成功する（上部の表示が変わる）
  await expect(header(page).getByText(`${secondNickname} さん`)).toBeVisible()
  currentNickname = secondNickname
  // timeout：失敗すると分かっているので、待つ時間を短くする（ミリ秒）
  await expect(main(page).getByRole('status')).toHaveText('ニックネームを変更しました。', { timeout: 3_000 })
})

test('ニックネームを空にすると、エラーが出て、変わらない', async () => {
  // 【何のため】入力のチェックが働き、エラーが入力欄の下に出ることを確かめる
  const field = page.getByLabel('新しいニックネーム')
  await field.fill('')
  await page.getByRole('button', { name: '変更する', exact: true }).click()
  // aria-invalid="true"：読み上げソフトに「この入力欄に誤りがある」と伝える印
  await expect(field).toHaveAttribute('aria-invalid', 'true')
  await expect(header(page).getByText(`${currentNickname} さん`)).toBeVisible()
})

test('アカウント設定で、パスワードを変えられ、ログインしたままになる', async () => {
  // 【何のため】パスワードの変更（F-24）の画面の動きを確かめる。確認の入力が違うときと、今のパスワードが違うときは
  // エラーが出て変わらないこと、変えた後も、この端末はログインしたままで、新しいセッションが使えることを確かめる
  await page.goto('/account')
  const newPassword = `${extra.password}-new`
  const current = page.getByLabel('今のパスワード', { exact: true })
  const next = page.getByLabel('新しいパスワード', { exact: true })
  const confirm = page.getByLabel('新しいパスワード（確認）', { exact: true })
  const submit = page.getByRole('button', { name: 'パスワードを変更する' })

  // 確認の入力が違うと、送らずに、確認の入力欄にエラーが出る
  await current.fill(extra.password)
  await next.fill(newPassword)
  await confirm.fill(`${newPassword}-typo`)
  await submit.click()
  await expect(confirm).toHaveAttribute('aria-invalid', 'true')

  // 今のパスワードが違うと、今のパスワードの入力欄にエラーが出る
  await current.fill(`${extra.password}-wrong`)
  await confirm.fill(newPassword)
  await submit.click()
  await expect(current).toHaveAttribute('aria-invalid', 'true')

  // 正しく入力すると、変更できたことが知らされ、入力欄は空に戻る
  await current.fill(extra.password)
  await submit.click()
  await expect(main(page).getByRole('status')).toContainText('パスワードを変更しました。')
  await expect(current).toHaveValue('')
  // 後片付け（global-teardown）と、次の削除のテストのために、新しいパスワードを覚えておく
  extra.password = newPassword
  const me = await (await page.request.get('/api/me')).json()
  remember({ ...extra, id: me.id })

  // 画面を読み込み直しても、ログインしたまま（サーバーが作り直した新しいセッションで続けられる）
  await page.reload()
  await expect(header(page).getByText(`${currentNickname} さん`)).toBeVisible()
})

test('アカウントを削除すると、ログインしていない状態になる', async () => {
  // 【何のため】パスワードで確認してから削除でき、削除したあとはログインしていない状態に戻ることを確かめる
  await page.goto('/account')
  await page.getByRole('button', { name: 'アカウントを削除する' }).click()

  // 「やめる」を押すと、確認の欄が閉じる（間違って押したときに戻れる）
  await page.getByRole('button', { name: 'やめる' }).click()
  await expect(page.getByRole('button', { name: '削除を確定する' })).toHaveCount(0)

  await page.getByRole('button', { name: 'アカウントを削除する' }).click()
  await page.getByLabel('確認のため、パスワードを入力してください').fill(extra.password)
  await page.getByRole('button', { name: '削除を確定する' }).click()

  await expect(header(page).getByRole('link', { name: 'ログイン' })).toBeVisible()
  await expect(header(page).getByText(`${currentNickname} さん`)).toHaveCount(0)
  // 削除したあとは、自分の情報を読めない（401：ログインしていない）
  expect((await page.request.get('/api/me')).status()).toBe(401)
  // 退会できたので、後片付け（global-teardown）の一覧から消す（後片付けで、ログインの回数を使わないため）
  forget(extra.email)
})

test('アカウントを削除したあとは、トップの画面に移る', async () => {
  // 【何のため】削除のあと、画面の案内どおり、トップの画面へ移ることを確かめる。
  // （以前は、ログイン状態が先に変わり、RequireAuth がログインの画面へ移動させていた。AuthProvider の becomeGuest で直した）
  await expect(page).toHaveURL('/', { timeout: 3_000 })
})

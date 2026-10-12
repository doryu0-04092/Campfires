/**
 * 【役割】画面のテスト（tests/browser）で、何度も使う準備や操作をまとめたファイル。
 * 【なぜ必要か】コミュニティやイベントを作る、メンバーにする、別の利用者でブラウザを開く、などの準備は、
 * どのテストでも同じなので、1か所にまとめて、テストの本文を「確かめたいこと」だけにするため。
 *
 * ここでの準備は、画面ではなく API で行う（準備はテストしたいことではないので、速く・確実に済ませる）。
 * このファイルの名前は .spec.ts で終わらないので、Playwright はテストとしては実行しない（部品として読み込むだけ）。
 */
import { expect, type Browser, type BrowserContext, type Page } from '@playwright/test'

import { account, sessionOf, browserStatePath, type Role } from '../../support/accounts'
import { ApiSession, eventInput, json } from '../../support/api'

/** 【何のため】API でコミュニティを作り、その番号（id）を返す（作った人がオーナーになる） */
export async function createCommunityViaApi(owner: ApiSession, name: string, description = 'E2E テストで作ったコミュニティです。') {
  // json<型>(返事, 201) は「201（作成した）が返ってくるはず。中身を読んで返す」
  const created = await json<{ id: number }>(await owner.post('/api/communities', { name, description }), 201)
  return created.id
}

/** 【何のため】API でイベントを作り、その番号（id）を返す。overrides で、変えたい項目だけを渡す */
export async function createEventViaApi(owner: ApiSession, communityId: number, overrides: Record<string, unknown> = {}) {
  const created = await json<{ id: number }>(
    await owner.post(`/api/communities/${communityId}/events`, eventInput(overrides)),
    201,
  )
  return created.id
}

/**
 * 【何のため】API で、その役の利用者を、コミュニティのメンバーにする（参加を申請して、オーナーが承認する）。
 */
export async function joinViaApi(owner: ApiSession, role: Role, communityId: number) {
  const user = await sessionOf(role)
  try {
    const request = await json<{ id: number }>(await user.post(`/api/communities/${communityId}/join-requests`), 201)
    await json(await owner.patch(`/api/communities/${communityId}/join-requests/${request.id}`, { status: 'approved' }))
  } finally {
    await user.dispose()
  }
}

/** 【何のため】その役の利用者の、お知らせをすべて既読にする（前のテストのお知らせが、件数の確認に混ざらないように） */
export async function readAllNotificationsViaApi(role: Role) {
  const user = await sessionOf(role)
  try {
    await user.post('/api/notifications/read-all')
  } finally {
    await user.dispose()
  }
}

/**
 * 【何のため】今から days 日後の hour 時ちょうどを、日付と時刻の入力欄（datetime-local）に入れる形で返す。
 * 例：2026-10-22T19:00。ブラウザは日本時間（playwright.config.ts の timezoneId）なので、日本時間で作る。
 */
export function localDateTime(days: number, hour: number): string {
  // 日本時間は、世界標準時より 9時間進んでいる。9時間足してから、世界標準時の読み方（getUTC〜）で読むと、日本時間になる
  const date = new Date(Date.now() + days * 24 * 60 * 60 * 1000 + 9 * 60 * 60 * 1000)
  // padStart(2, '0')：1桁の数の前に 0 を付けて、2桁にする（例：7 → 07）
  const two = (n: number) => String(n).padStart(2, '0')
  return `${date.getUTCFullYear()}-${two(date.getUTCMonth() + 1)}-${two(date.getUTCDate())}T${two(hour)}:00`
}

/**
 * 【何のため】別の利用者として、新しいブラウザ（の画面）を開く。
 * 1つのテストの中で、オーナーとメンバーなど、2人以上の操作を確かめるときに使う。
 * 使い終わったら、返した context を close() で閉じる。
 */
export async function openPageAs(browser: Browser, role: Role): Promise<{ context: BrowserContext; page: Page }> {
  // storageState：保存しておいたログインの Cookie を入れた状態で、ブラウザを始める
  const context = await browser.newContext({ storageState: browserStatePath(role) })
  const page = await context.newPage()
  return { context, page }
}

/** 【何のため】画面上部のメニュー（ログイン状態によって中身が変わる場所） */
export function header(page: Page) {
  return page.locator('header')
}

/** 【何のため】画面左のメニュー */
export function sidebar(page: Page) {
  return page.locator('aside')
}

/** 【何のため】画面の真ん中（各画面の中身） */
export function main(page: Page) {
  return page.locator('main')
}

/** 【何のため】ログインした状態の画面が表示されるまで待つ（上部に「〇〇 さん」が出る） */
export async function expectLoggedInAs(page: Page, role: Role) {
  await expect(header(page).getByText(`${account(role).nickname} さん`)).toBeVisible()
}

/**
 * 【何のため】次に出る確認のダイアログ（window.confirm）で「OK」を押す準備をする。
 * once は「1回だけ」という意味。クリックの前に呼んでおく。
 */
export function acceptNextDialog(page: Page) {
  page.once('dialog', (dialog) => dialog.accept())
}

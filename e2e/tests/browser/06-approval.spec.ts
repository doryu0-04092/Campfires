/**
 * 【役割】手動で承認するイベントの流れ（申込み → 承認待ち → 承認・却下）と、そのお知らせを確かめるテスト。
 * 【なぜ必要か】手動の承認では、オーナー（運営スタッフ）と申し込んだ人の、2人の画面がつながって動く。
 * 承認・却下の結果が、申し込んだ人の画面とお知らせに正しく届くことを、本物のブラウザで確かめるため。
 *
 * 確かめる主な機能：
 * - 手動で承認するイベントに申し込む → 承認待ち
 * - オーナーが、申込者一覧の画面で承認・却下する
 * - 申し込んだ人の画面に、結果（申込み済み・承認されませんでした）が出る
 * - お知らせ：画面上部に未読の件数が出る。お知らせ一覧に出て、開くと既読になる。「すべて既読にする」
 *
 * 前のテストの結果（申込み・承認）を、次のテストで使うので、serial にしている。
 */
import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test'

import { account, tag, sessionOf } from '../../support/accounts'
import {
  acceptNextDialog,
  createCommunityViaApi,
  createEventViaApi,
  header,
  joinViaApi,
  main,
  openPageAs,
  readAllNotificationsViaApi,
} from './helpers'

test.describe.configure({ mode: 'serial' })

let communityId: number
let eventId: number
let eventTitle: string
let eventPath: string
const pages = {} as Record<'owner' | 'member' | 'outsider', Page>
const contexts: BrowserContext[] = []

// 【何のため】コミュニティ（メンバーが参加済み）と、手動で承認するイベントを API で作り、3人のブラウザを開いておく
test.beforeAll(async ({ browser }: { browser: Browser }) => {
  eventTitle = `E2E 手動承認のイベント ${tag()}`
  const owner = await sessionOf('owner')
  try {
    communityId = await createCommunityViaApi(owner, `E2E 承認用コミュニティ ${tag()}`)
    await joinViaApi(owner, 'member', communityId)
    eventId = await createEventViaApi(owner, communityId, {
      title: eventTitle,
      capacity: 5,
      memberApproval: 'manual',
      nonMemberApproval: 'manual',
    })
  } finally {
    await owner.dispose()
  }
  eventPath = `/communities/${communityId}/events/${eventId}`
  // 前のテストのお知らせが、件数の確認に混ざらないように、先に既読にしておく
  await readAllNotificationsViaApi('member')
  await readAllNotificationsViaApi('outsider')
  for (const role of ['owner', 'member', 'outsider'] as const) {
    const opened = await openPageAs(browser, role)
    contexts.push(opened.context)
    pages[role] = opened.page
  }
})

test.afterAll(async () => {
  for (const context of contexts) await context.close()
})

/** 【何のため】申込者一覧の画面で、その人の行（カード）を探す */
function applicantRow(page: Page, nickname: string) {
  return main(page).locator('li', { hasText: nickname })
}

test('手動で承認するイベントに申し込むと「承認待ち」になる', async () => {
  // 【何のため】手動のときは、申し込んでもすぐには席が確保されず、承認を待つことを確かめる
  for (const role of ['member', 'outsider'] as const) {
    const page = pages[role]
    await page.goto(eventPath)
    await expect(main(page).getByText('申し込むと、オーナーか運営スタッフの承認を待ちます。', { exact: false })).toBeVisible()
    await main(page).getByRole('button', { name: '申し込む', exact: true }).click()
    await expect(main(page).getByText(/承認を待っています。/)).toBeVisible()
    await expect(main(page).getByRole('button', { name: '申込みを取り消す' })).toBeVisible()
  }
  // 席はまだ確保されていない（残り 5人のまま、承認待ち 2人）
  await expect(main(pages.outsider).getByText('5人（残り 5人、承認待ち 2人）')).toBeVisible()

  // トップの「参加予定のイベント」には、承認待ちの札で出る
  await pages.member.goto('/')
  await expect(main(pages.member).locator('article', { hasText: eventTitle }).getByText('承認待ち')).toBeVisible()
})

test('オーナーが、申込者一覧の画面で承認・却下できる', async () => {
  // 【何のため】承認すると席が確保され、却下すると一覧から外れることを確かめる
  const page = pages.owner
  await page.goto(eventPath)
  await main(page).getByRole('link', { name: '申込者一覧' }).click()
  await expect(page.getByRole('heading', { name: `申込者一覧：${eventTitle}` })).toBeVisible()
  await expect(main(page).getByText('申込み済み 0人 / 定員 5人、キャンセル待ち 0人、承認待ち 2人')).toBeVisible()

  const memberRow = applicantRow(page, account('member').nickname)
  await expect(memberRow.getByText('承認待ち', { exact: true })).toBeVisible()
  await memberRow.getByRole('button', { name: '承認する' }).click()
  await expect(memberRow.getByText('申込み済み', { exact: true })).toBeVisible()
  await expect(memberRow.getByRole('button', { name: '承認する' })).toHaveCount(0)

  // 却下は、確認のダイアログが出るので、OK を押す
  acceptNextDialog(page)
  await applicantRow(page, account('outsider').nickname).getByRole('button', { name: '却下する' }).click()
  // 却下した人は、申込者一覧から外れる
  await expect(applicantRow(page, account('outsider').nickname)).toHaveCount(0)
  await expect(main(page).getByText('申込み済み 1人 / 定員 5人、キャンセル待ち 0人、承認待ち 0人')).toBeVisible()
})

test('申し込んだ人の画面に、承認・却下の結果が出る', async () => {
  // 【何のため】結果が、申し込んだ本人に伝わることを確かめる
  await pages.member.goto(eventPath)
  await expect(main(pages.member).getByText('申込み済みです。')).toBeVisible()

  await pages.outsider.goto(eventPath)
  await expect(main(pages.outsider).getByText('このイベントへの参加は、承認されませんでした。')).toBeVisible()
  // 却下された人は、申し込み直せない
  await expect(main(pages.outsider).getByRole('button', { name: /申し込む/ })).toHaveCount(0)
})

test('承認されたメンバーに、未読のお知らせが届き、開くと既読になる', async () => {
  // 【何のため】承認のお知らせが届き、画面上部の未読の件数と、お知らせ一覧で確かめられることを確かめる
  const page = pages.member
  await page.goto('/')
  // 未読があると、「お知らせ」のリンクの読み上げ用の名前に、件数が入る
  await expect(header(page).getByRole('link', { name: 'お知らせ（未読 1件）' })).toBeVisible()
  await header(page).getByRole('link', { name: /お知らせ/ }).click()
  await expect(page).toHaveURL('/notifications')
  await expect(main(page).getByText('未読 1件')).toBeVisible()

  const item = main(page).locator('li', { hasText: `「${eventTitle}」への参加が承認されました。` })
  await expect(item.getByText('未読', { exact: true })).toBeVisible()
  // 「イベントを見る」を押すと、既読になり、イベントの詳細へ移る
  await item.getByRole('button', { name: 'イベントを見る' }).click()
  await expect(page).toHaveURL(eventPath)
  await expect(header(page).getByRole('link', { name: 'お知らせ', exact: true })).toBeVisible()

  // お知らせ一覧に戻ると、既読になっている（お知らせは消えずに残る）
  await page.goto('/notifications')
  await expect(main(page).getByText('未読 0件')).toBeVisible()
  await expect(main(page).locator('li', { hasText: `「${eventTitle}」への参加が承認されました。` })).toBeVisible()
  await expect(main(page).getByRole('button', { name: 'すべて既読にする' })).toHaveCount(0)
})

test('却下された人には、却下のお知らせが届き、すべて既読にできる', async () => {
  // 【何のため】「すべて既読にする」で、未読の件数が 0 になることを確かめる
  const page = pages.outsider
  await page.goto('/notifications')
  await expect(header(page).getByRole('link', { name: 'お知らせ（未読 1件）' })).toBeVisible()
  const item = main(page).locator('li', { hasText: `「${eventTitle}」への参加は、承認されませんでした。` })
  await expect(item.getByText('未読', { exact: true })).toBeVisible()

  await main(page).getByRole('button', { name: 'すべて既読にする' }).click()
  await expect(main(page).getByText('未読 0件')).toBeVisible()
  await expect(item.getByText('未読', { exact: true })).toHaveCount(0)
  await expect(header(page).getByRole('link', { name: 'お知らせ', exact: true })).toBeVisible()
})

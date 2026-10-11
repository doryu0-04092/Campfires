/**
 * 【役割】イベントへの申込み・キャンセル・キャンセル待ちを、メンバーとメンバー以外の画面から確かめるテスト。
 * 【なぜ必要か】定員と順番の決まり（満席ならキャンセル待ち、空きが出たら自動で繰り上がる）は間違えやすく、
 * 間違うと、参加できるはずの人が参加できなくなるため。
 *
 * 確かめる主な機能：
 * - 定員 1人・自動で承認のイベントに、メンバーが申し込む → 申込み済み（トップの「参加予定のイベント」にも出る）
 * - 満席のときに、メンバー以外の人が申し込む → キャンセル待ち 1番目
 * - メンバーがキャンセルする → キャンセル待ちの人が、自動で申込み済みに繰り上がる
 * - メンバー以外を受け付けないイベントでは、メンバー以外の人に「コミュニティに参加してください」と出る
 *
 * 申込みの状況が、前のテストの結果で変わっていくので、serial にしている。
 */
import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test'

import { runId, sessionOf } from '../../support/accounts'
import { acceptNextDialog, createCommunityViaApi, createEventViaApi, joinViaApi, main, openPageAs } from './helpers'

test.describe.configure({ mode: 'serial' })

let communityId: number
let eventId: number
let closedEventId: number
let eventTitle: string
const pages = {} as Record<'member' | 'outsider', Page>
const contexts: BrowserContext[] = []

// 【何のため】コミュニティ（メンバーが参加済み）と、2つのイベントを API で作り、2人のブラウザを開いておく
test.beforeAll(async ({ browser }: { browser: Browser }) => {
  eventTitle = `E2E 定員1人のイベント ${runId()}`
  const owner = await sessionOf('owner')
  try {
    communityId = await createCommunityViaApi(owner, `E2E 申込み用コミュニティ ${runId()}`)
    await joinViaApi(owner, 'member', communityId)
    // 定員 1人。メンバーもメンバー以外も、申し込むとすぐに参加が決まる（自動）
    eventId = await createEventViaApi(owner, communityId, {
      title: eventTitle,
      capacity: 1,
      memberApproval: 'auto',
      nonMemberApproval: 'auto',
    })
    // メンバー以外は受け付けないイベント
    closedEventId = await createEventViaApi(owner, communityId, {
      title: `E2E メンバー限定イベント ${runId()}`,
      nonMemberApproval: 'closed',
    })
  } finally {
    await owner.dispose()
  }
  for (const role of ['member', 'outsider'] as const) {
    const opened = await openPageAs(browser, role)
    contexts.push(opened.context)
    pages[role] = opened.page
  }
})

test.afterAll(async () => {
  for (const context of contexts) await context.close()
})

test('メンバーが申し込むと「申込み済み」になり、参加予定のイベントに出る', async () => {
  // 【何のため】空きのあるイベントに申し込むと、すぐに参加が決まることを確かめる
  const page = pages.member
  await page.goto(`/communities/${communityId}/events/${eventId}`)
  await expect(main(page).getByText('1人（残り 1人）')).toBeVisible()
  await main(page).getByRole('button', { name: '申し込む', exact: true }).click()
  await expect(main(page).getByText('申込み済みです。')).toBeVisible()
  await expect(main(page).getByText('1人（残り 0人）')).toBeVisible()

  // トップの画面の「参加予定のイベント」に、札（申込み済み）付きで出る
  await page.goto('/')
  const card = main(page).locator('article', { hasText: eventTitle })
  await expect(card.getByText('申込み済み')).toBeVisible()
  // 残りの枠は「満席」
  await expect(card.getByText('満席')).toBeVisible()
})

test('満席のイベントに、メンバー以外の人が申し込むと「キャンセル待ち 1番目」になる', async () => {
  // 【何のため】満席のときは、キャンセル待ちとして申し込めることを確かめる
  const page = pages.outsider
  await page.goto(`/communities/${communityId}/events/${eventId}`)
  await expect(main(page).getByText('満席です。キャンセル待ちとして申し込めます。')).toBeVisible()
  await main(page).getByRole('button', { name: 'キャンセル待ちに申し込む' }).click()
  await expect(main(page).getByText('キャンセル待ちです（1番目）。', { exact: false })).toBeVisible()
  await expect(main(page).getByRole('button', { name: 'キャンセル待ちをやめる' })).toBeVisible()
  await expect(main(page).getByText('1人（残り 0人、キャンセル待ち 1人）')).toBeVisible()

  await page.goto('/')
  await expect(main(page).locator('article', { hasText: eventTitle }).getByText('キャンセル待ち 1番目')).toBeVisible()
})

test('メンバーがキャンセルすると、キャンセル待ちの人が繰り上がる', async () => {
  // 【何のため】空きが出たら、キャンセル待ちの1番目の人が、自動で参加できるようになることを確かめる
  const member = pages.member
  await member.goto(`/communities/${communityId}/events/${eventId}`)
  acceptNextDialog(member)
  await main(member).getByRole('button', { name: '申込みをキャンセルする' }).click()
  // キャンセルした席は、すぐに繰り上がった人のものになるので、メンバーの画面では満席になっている
  await expect(main(member).getByRole('button', { name: 'キャンセル待ちに申し込む' })).toBeVisible()
  await member.goto('/')
  await expect(main(member).locator('article', { hasText: eventTitle })).toHaveCount(0)

  // メンバー以外の人の画面：申込み済みになっている
  const outsider = pages.outsider
  await outsider.goto(`/communities/${communityId}/events/${eventId}`)
  await expect(main(outsider).getByText('申込み済みです。')).toBeVisible()
  await outsider.goto('/')
  await expect(main(outsider).locator('article', { hasText: eventTitle }).getByText('申込み済み')).toBeVisible()
})

test('メンバー以外を受け付けないイベントには、メンバー以外の人は申し込めない', async () => {
  // 【何のため】メンバー以外の人には、申し込むボタンの代わりに、コミュニティへの参加の案内が出ることを確かめる
  const page = pages.outsider
  await page.goto(`/communities/${communityId}/events/${closedEventId}`)
  await expect(main(page).getByText('メンバー：自動で承認、メンバー以外：受け付けない')).toBeVisible()
  await expect(main(page).getByText(/このイベントに申し込むには、\s*コミュニティ\s*に参加してください。/)).toBeVisible()
  await expect(main(page).getByRole('button', { name: /申し込む/ })).toHaveCount(0)

  // メンバーには、申し込むボタンが出る
  await pages.member.goto(`/communities/${communityId}/events/${closedEventId}`)
  await expect(main(pages.member).getByRole('button', { name: '申し込む', exact: true })).toBeVisible()
})

/**
 * 【役割】メンバー本人のための画面（参加予定のイベント、参加しているコミュニティ）を確かめるテスト。
 * 【なぜ必要か】ログインした人が最初に見る画面で、「自分が何に申し込み、どこに参加しているか」がひと目で分かる必要があるため。
 *
 * 確かめる主な機能：
 * - トップ（/）の「参加予定のイベント」に、申し込んだイベントが、申込みの状況の札付きで出る
 * - コミュニティ一覧（/communities）の「参加しているコミュニティ」に、メンバーとして参加しているコミュニティが出る
 * - 運営スタッフをしているコミュニティは「参加しているコミュニティ」ではなく「管理しているコミュニティ」に出る
 */
import { expect, test } from '@playwright/test'

import { account, runId, sessionOf, storageStatePath } from '../../support/accounts'
import { json } from '../../support/api'
import { createCommunityViaApi, createEventViaApi, joinViaApi, main } from './helpers'

// このファイルのテストは、メンバーとしてログインした状態で行う
test.use({ storageState: storageStatePath('member') })

let memberCommunityName: string
let staffCommunityName: string
let eventTitle: string
let communityId: number
let eventId: number

// 【何のため】メンバーとして参加するコミュニティ、運営スタッフをするコミュニティ、申し込むイベントを、API で用意する
test.beforeAll(async () => {
  memberCommunityName = `E2E 参加中コミュニティ ${runId()}`
  staffCommunityName = `E2E スタッフ中コミュニティ ${runId()}`
  eventTitle = `E2E 参加予定イベント ${runId()}`
  const owner = await sessionOf('owner')
  const member = await sessionOf('member')
  try {
    communityId = await createCommunityViaApi(owner, memberCommunityName)
    await joinViaApi(owner, 'member', communityId)
    eventId = await createEventViaApi(owner, communityId, { title: eventTitle })
    // メンバーとして申し込む（201：申し込めた）
    await json(await member.post(`/api/communities/${communityId}/events/${eventId}/applications`), 201)

    const staffCommunityId = await createCommunityViaApi(owner, staffCommunityName)
    await joinViaApi(owner, 'member', staffCommunityId)
    await json(await owner.patch(`/api/communities/${staffCommunityId}/members/${account('member').id}`, { role: 'staff' }))
  } finally {
    await owner.dispose()
    await member.dispose()
  }
})

test('トップの「参加予定のイベント」に、申し込んだイベントが出る', async ({ page }) => {
  // 【何のため】申し込んだイベントが、日時・コミュニティ・申込みの状況と一緒に、一覧で分かることを確かめる
  await page.goto('/')
  await expect(main(page).getByRole('heading', { name: '参加予定のイベント' })).toBeVisible()
  await expect(main(page).getByText(/\d+件（開催日の近い順）/)).toBeVisible()
  const card = main(page).locator('article', { hasText: eventTitle })
  await expect(card.getByText('申込み済み')).toBeVisible()
  await expect(card.getByRole('link', { name: memberCommunityName })).toBeVisible()
  // 残りの枠（定員 5人のうち、1人が申し込み済み）
  await expect(card.getByText('4 / 5')).toBeVisible()

  // タイトルを押すと、イベントの詳細へ移る
  await card.getByRole('link', { name: eventTitle }).click()
  await expect(page).toHaveURL(`/communities/${communityId}/events/${eventId}`)
  await expect(main(page).getByText('申込み済みです。')).toBeVisible()
})

test('コミュニティ一覧の「参加しているコミュニティ」に、メンバーとして参加しているコミュニティが出る', async ({ page }) => {
  // 【何のため】自分が参加しているコミュニティが一覧で分かり、そこから詳細へ移れることを確かめる
  await page.goto('/communities')
  await expect(main(page).getByRole('heading', { name: '参加しているコミュニティ' })).toBeVisible()
  await expect(main(page).getByRole('link', { name: memberCommunityName })).toBeVisible()
  // 運営スタッフをしているコミュニティは、ここには出ない
  await expect(main(page).getByRole('link', { name: staffCommunityName })).toHaveCount(0)

  await main(page).getByRole('link', { name: memberCommunityName }).click()
  await expect(page).toHaveURL(`/communities/${communityId}`)
  await expect(main(page).getByText('あなたの立場：メンバー')).toBeVisible()
})

test('運営スタッフをしているコミュニティは、「管理しているコミュニティ」に出る', async ({ page }) => {
  // 【何のため】管理の役目があるコミュニティは、管理の画面の一覧で、立場（運営スタッフ）と一緒に分かることを確かめる
  await page.goto('/manage/communities')
  const card = main(page).locator('li', { hasText: staffCommunityName })
  await expect(card.getByText('運営スタッフ', { exact: true })).toBeVisible()
  // 運営スタッフは、編集（オーナーだけ）はできないが、メンバー管理とイベント作成はできる
  await expect(card.getByRole('link', { name: '編集' })).toHaveCount(0)
  await expect(card.getByRole('link', { name: 'メンバー管理' })).toBeVisible()
  await expect(card.getByRole('link', { name: 'イベントを作る' })).toBeVisible()
  // メンバーとして参加しているだけのコミュニティは、ここには出ない
  await expect(main(page).getByRole('link', { name: memberCommunityName })).toHaveCount(0)
})

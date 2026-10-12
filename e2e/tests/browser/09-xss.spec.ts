/**
 * 【役割】XSS（クロスサイトスクリプティング）が効かないことを、本物のブラウザで確かめるテスト。
 * 【なぜ必要か】XSS は、入力欄にプログラム（<script> など）を書き込み、それを見たほかの人のブラウザで動かす攻撃。
 * このアプリは、利用者が書いた文字（コミュニティの名前・説明、イベントのタイトル・説明・場所、ニックネーム）を、
 * ほかの人の画面に出すので、それが「プログラム」ではなく「ただの文字」として出ることを確かめる。
 *
 * 確かめ方：攻撃の文字が動くと window.__xss に 1 が入る（または、ダイアログが出る）ようにしておき、
 * 画面を開いたあとで、window.__xss が入っていないこと・ダイアログが出ていないこと・文字がそのまま見えることを確かめる。
 *
 * 確かめる主な機能：
 * - コミュニティの名前・説明（一覧・検索・詳細・管理の画面）
 * - イベントのタイトル・説明・場所（トップの検索・詳細・選ぶ画面）
 * - ニックネーム（画面上部、申込者一覧、メンバー管理）
 * - 画面の入力欄（コミュニティ作成）から入れた場合
 */
import { expect, test, type Page } from '@playwright/test'

import { account, tag, sessionOf, browserStatePath } from '../../support/accounts'
import { json } from '../../support/api'
import { createCommunityViaApi, createEventViaApi, header, joinViaApi, main, openPageAs } from './helpers'

test.describe.configure({ mode: 'serial' })

// 攻撃の文字。動いてしまうと window.__xss に 1 が入る
const IMG = '<img src=x onerror="window.__xss=1">'
const SCRIPT = '<script>window.__xss=1</script>'
// ニックネームは 30文字までなので、短い形にする（svg の onload も、動けばプログラムが実行される）
const NICKNAME = '<svg onload=window.__xss=1>'

let communityId: number
let eventId: number
let communityName: string
let eventTitle: string

// 【何のため】攻撃の文字を入れたコミュニティ・イベント・ニックネームを、API で用意する
test.beforeAll(async () => {
  // 名前は 50文字まで
  communityName = `${IMG} ${tag()}`
  eventTitle = `${IMG}${SCRIPT} ${tag()}`
  const owner = await sessionOf('owner')
  const member = await sessionOf('member')
  try {
    communityId = await createCommunityViaApi(owner, communityName, `説明 ${IMG} ${SCRIPT}`)
    await joinViaApi(owner, 'member', communityId)
    eventId = await createEventViaApi(owner, communityId, {
      title: eventTitle,
      description: `イベントの説明 ${SCRIPT}`,
      location: `javascript:window.__xss=1 ${IMG}`,
    })
    // メンバーのニックネームを、攻撃の文字にして、申し込む（申込者一覧とメンバー管理に出す）
    await json(await member.patch('/api/me', { nickname: NICKNAME }))
    await json(await member.post(`/api/communities/${communityId}/events/${eventId}/applications`), 201)
  } finally {
    await owner.dispose()
    await member.dispose()
  }
})

// 【何のため】最後に、メンバーのニックネームを元に戻す（ほかのテストが、ニックネームでメンバーを探すため）
test.afterAll(async () => {
  const member = await sessionOf('member')
  try {
    await json(await member.patch('/api/me', { nickname: account('member').nickname }))
  } finally {
    await member.dispose()
  }
})

/**
 * 【何のため】画面で、ダイアログ（alert など）が出たら覚えておく。出たら、攻撃のプログラムが動いたということ。
 * 返した配列に、出たダイアログの文が入る
 */
function watchDialogs(page: Page): string[] {
  const dialogs: string[] = []
  page.on('dialog', async (dialog) => {
    dialogs.push(dialog.message())
    await dialog.dismiss()
  })
  return dialogs
}

/** 【何のため】攻撃のプログラムが動いていないことを確かめる */
async function expectNoXss(page: Page, dialogs: string[]) {
  // window.__xss が undefined（何も入っていない）なら、プログラムは動いていない。
  // (window as any)：TypeScript に「window に __xss という項目があってもよい」と伝える書き方
  expect(await page.evaluate(() => (window as any).__xss)).toBeUndefined()
  expect(dialogs).toEqual([])
  // 攻撃の文字から、本物の img・script・svg の部品が作られていないこと
  expect(await main(page).locator('img[src="x"], svg[onload]').count()).toBe(0)
}

test.describe('オーナーの画面', () => {
  test.use({ storageState: browserStatePath('owner') })

  test('コミュニティの名前と説明が、文字のまま出る', async ({ page }) => {
    // 【何のため】一覧・検索・詳細・管理の画面で、名前と説明の攻撃の文字が動かないことを確かめる
    const dialogs = watchDialogs(page)
    await page.goto(`/communities?q=${encodeURIComponent(tag())}`)
    await expect(main(page).getByRole('link', { name: communityName })).toBeVisible()
    await expectNoXss(page, dialogs)

    await page.goto(`/communities/${communityId}`)
    await expect(page.getByRole('heading', { name: communityName, level: 1 })).toBeVisible()
    await expect(main(page).getByText(`説明 ${IMG} ${SCRIPT}`)).toBeVisible()
    await expectNoXss(page, dialogs)

    for (const path of ['/manage/communities', '/manage/select/members', `/manage/communities/${communityId}/edit`]) {
      await page.goto(path)
      await expect(main(page).getByText(communityName).first()).toBeVisible()
      await expectNoXss(page, dialogs)
    }
    // 編集の画面の入力欄には、攻撃の文字が、そのまま値として入っている
    await expect(page.getByLabel('コミュニティの名前')).toHaveValue(communityName)
  })

  test('イベントのタイトル・説明・場所が、文字のまま出る', async ({ page }) => {
    // 【何のため】トップの検索・詳細・選ぶ画面で、イベントの攻撃の文字が動かないことを確かめる
    const dialogs = watchDialogs(page)
    await page.goto(`/?q=${encodeURIComponent(tag())}`)
    await expect(main(page).getByRole('link', { name: eventTitle })).toBeVisible()
    await expectNoXss(page, dialogs)

    await page.goto(`/communities/${communityId}/events/${eventId}`)
    await expect(page.getByRole('heading', { name: eventTitle, level: 1 })).toBeVisible()
    await expect(main(page).getByText(`イベントの説明 ${SCRIPT}`)).toBeVisible()
    await expect(main(page).getByText(`javascript:window.__xss=1 ${IMG}`)).toBeVisible()
    // 場所の「javascript:」が、押せるリンクになっていない
    expect(await page.locator('a[href^="javascript:"]').count()).toBe(0)
    await expectNoXss(page, dialogs)

    await page.goto('/manage/select/applicants')
    await expect(main(page).getByRole('link', { name: eventTitle })).toBeVisible()
    await expectNoXss(page, dialogs)
  })

  test('メンバーのニックネームが、申込者一覧とメンバー管理で、文字のまま出る', async ({ page }) => {
    // 【何のため】ほかの人のニックネームを見る画面で、攻撃の文字が動かないことを確かめる
    const dialogs = watchDialogs(page)
    await page.goto(`/manage/communities/${communityId}/events/${eventId}/applicants`)
    await expect(main(page).getByText(NICKNAME)).toBeVisible()
    await expectNoXss(page, dialogs)

    await page.goto(`/manage/communities/${communityId}/members`)
    await expect(main(page).getByText(NICKNAME)).toBeVisible()
    await expectNoXss(page, dialogs)
  })

  test('画面の入力欄から入れた攻撃の文字も、文字のまま出る', async ({ page }) => {
    // 【何のため】API ではなく、画面の入力欄から入れた場合も、同じように安全なことを確かめる
    const dialogs = watchDialogs(page)
    const name = `${SCRIPT} ${tag()}`
    await page.goto('/manage/communities/new')
    await page.getByLabel('コミュニティの名前').fill(name)
    await page.getByLabel('説明').fill(`${IMG}\n<a href="javascript:window.__xss=1">押してね</a>`)
    await page.getByRole('button', { name: '作成する' }).click()
    await expect(page).toHaveURL('/manage/communities')

    await main(page).getByRole('link', { name }).click()
    await expect(page.getByRole('heading', { name, level: 1 })).toBeVisible()
    await expect(main(page).getByText('<a href="javascript:window.__xss=1">押してね</a>')).toBeVisible()
    // 説明の中の <a> は、本物のリンクになっていない
    await expect(main(page).getByRole('link', { name: '押してね' })).toHaveCount(0)
    await expectNoXss(page, dialogs)
  })
})

test('自分のニックネームが、画面上部とアカウント設定で、文字のまま出る', async ({ browser }) => {
  // 【何のため】ニックネームは、ログインしている間ずっと画面上部に出るので、そこで動かないことを確かめる
  const { context, page } = await openPageAs(browser, 'member')
  try {
    const dialogs = watchDialogs(page)
    await page.goto('/account')
    await expect(header(page).getByText(`${NICKNAME} さん`)).toBeVisible()
    await expect(main(page).getByText(NICKNAME, { exact: true })).toBeVisible()
    await expectNoXss(page, dialogs)
    // header の中にも、本物の svg の部品が作られていない
    expect(await header(page).locator('svg[onload]').count()).toBe(0)
  } finally {
    await context.close()
  }
})

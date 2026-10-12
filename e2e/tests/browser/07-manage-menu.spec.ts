/**
 * 【役割】左のメニューの「オーナー・運営スタッフ用」（管理メニュー）と、そこから開く「選ぶ画面」を確かめるテスト。
 * 【なぜ必要か】管理の画面の多くは、どのコミュニティ・どのイベントかを先に選ばないと開けない（Issue #45）。
 * メニュー → 選ぶ画面 → 管理の画面、とつながって移れること、
 * また、管理をしていない人には、管理の画面の中身が見えないことを確かめるため。
 *
 * 確かめる主な機能：
 * - 管理メニューは、最初は閉じている。開け閉めの状態は、ブラウザ（localStorage）に覚えられ、読み込み直しても続く
 * - 選ぶ画面（コミュニティ編集・メンバー管理・イベント作成・イベント編集・申込者一覧）に、作ったコミュニティ・イベントが出て、
 *   押すと、それぞれの管理の画面へ移る
 * - 管理をしていない人（outsider）は、選ぶ画面に何も出ず、管理の画面を URL で開いても「権限」の文が出る
 *
 * メモ：今のアプリは、管理メニューそのもの（開け閉めのボタン）を、管理をしていない人にも表示する。
 * ボタンを隠すのではなく、選ぶ画面と管理の画面で、中身を見せないつくり。
 */
import { expect, test, type Page } from '@playwright/test'

import { tag, sessionOf, browserStatePath } from '../../support/accounts'
import { createCommunityViaApi, createEventViaApi, main, sidebar } from './helpers'

// 管理メニューの開け閉めを覚えておく名前（src/components/Sidebar.tsx と同じ）
const MENU_KEY = 'campfires.sidebar.manageMenuOpen'

let communityId: number
let communityName: string
let eventId: number
let eventTitle: string

// 【何のため】オーナーとして、選ぶ画面に出すコミュニティとイベントを、API で作っておく
test.beforeAll(async () => {
  communityName = `E2E メニュー用コミュニティ ${tag()}`
  eventTitle = `E2E メニュー用イベント ${tag()}`
  const owner = await sessionOf('owner')
  try {
    communityId = await createCommunityViaApi(owner, communityName)
    eventId = await createEventViaApi(owner, communityId, { title: eventTitle })
  } finally {
    await owner.dispose()
  }
})

/** 【何のため】管理メニューを開け閉めするボタン */
function menuToggle(page: Page) {
  return sidebar(page).getByRole('button', { name: 'オーナー・運営スタッフ用' })
}

/** 【何のため】ブラウザに覚えている、管理メニューの状態を読む */
function storedMenuState(page: Page) {
  // page.evaluate：ブラウザの中で、プログラムを動かして、結果を受け取る
  return page.evaluate((key) => localStorage.getItem(key), MENU_KEY)
}

test.describe('オーナーの画面', () => {
  test.use({ storageState: browserStatePath('owner') })

  test('管理メニューは最初は閉じていて、開け閉めの状態が覚えられる', async ({ page }) => {
    // 【何のため】ふだん使わない人のために閉じておき、使う人は開いたままにできることを確かめる
    await page.goto('/')
    // aria-expanded：読み上げソフトに「開いているか」を伝える印。false なら閉じている
    await expect(menuToggle(page)).toHaveAttribute('aria-expanded', 'false')
    await expect(sidebar(page).getByRole('link', { name: 'コミュニティ作成' })).toBeHidden()
    // 「ユーザー」のまとまりは、いつも見えている
    await expect(sidebar(page).getByRole('link', { name: 'イベント一覧' })).toBeVisible()
    await expect(sidebar(page).getByRole('link', { name: 'コミュニティ一覧' })).toBeVisible()

    // 開く
    await menuToggle(page).click()
    await expect(menuToggle(page)).toHaveAttribute('aria-expanded', 'true')
    for (const name of ['管理しているコミュニティ', 'コミュニティ作成', 'コミュニティ編集', 'メンバー管理', 'イベント作成', 'イベント編集', '申込者一覧']) {
      await expect(sidebar(page).getByRole('link', { name, exact: true })).toBeVisible()
    }
    expect(await storedMenuState(page)).toBe('open')

    // 読み込み直しても、開いたまま
    await page.reload()
    await expect(menuToggle(page)).toHaveAttribute('aria-expanded', 'true')
    await expect(sidebar(page).getByRole('link', { name: 'コミュニティ作成' })).toBeVisible()

    // 閉じると、読み込み直しても、閉じたまま
    await menuToggle(page).click()
    await expect(sidebar(page).getByRole('link', { name: 'コミュニティ作成' })).toBeHidden()
    expect(await storedMenuState(page)).toBe('closed')
    await page.reload()
    await expect(menuToggle(page)).toHaveAttribute('aria-expanded', 'false')
  })

  test.describe('管理メニューから、選ぶ画面を通って、管理の画面へ移れる', () => {
    // 【何のため】それぞれのテストを、管理メニューが開いた状態から始める。
    // addInitScript：画面を開く前に、ブラウザの中でプログラムを動かす（ここでは「開いている」と覚えさせる）
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((key) => localStorage.setItem(key, 'open'), MENU_KEY)
      await page.goto('/')
    })

    test('管理しているコミュニティ・コミュニティ作成', async ({ page }) => {
      // 【何のため】選ばずに開ける2つの画面へ、メニューから移れることを確かめる
      await sidebar(page).getByRole('link', { name: '管理しているコミュニティ' }).click()
      await expect(page).toHaveURL('/manage/communities')
      await expect(main(page).getByRole('link', { name: communityName })).toBeVisible()

      await sidebar(page).getByRole('link', { name: 'コミュニティ作成' }).click()
      await expect(page).toHaveURL('/manage/communities/new')
      await expect(page.getByRole('heading', { name: 'コミュニティを作る' })).toBeVisible()
    })

    test('コミュニティ編集：コミュニティを選ぶと、編集の画面へ移る', async ({ page }) => {
      // 【何のため】コミュニティを選ぶ画面（S-18）から、編集の画面へ移れることを確かめる
      await sidebar(page).getByRole('link', { name: 'コミュニティ編集' }).click()
      await expect(page).toHaveURL('/manage/select/community-edit')
      await expect(page.getByRole('heading', { name: 'コミュニティ編集：コミュニティを選ぶ' })).toBeVisible()
      await main(page).getByRole('link', { name: communityName }).click()
      await expect(page).toHaveURL(`/manage/communities/${communityId}/edit`)
      await expect(page.getByRole('heading', { name: 'コミュニティを編集' })).toBeVisible()
    })

    test('メンバー管理：コミュニティを選ぶと、メンバー管理の画面へ移る', async ({ page }) => {
      // 【何のため】コミュニティを選ぶ画面から、メンバー管理の画面へ移れることを確かめる
      await sidebar(page).getByRole('link', { name: 'メンバー管理' }).click()
      await expect(page.getByRole('heading', { name: 'メンバー管理：コミュニティを選ぶ' })).toBeVisible()
      await main(page).getByRole('link', { name: communityName }).click()
      await expect(page).toHaveURL(`/manage/communities/${communityId}/members`)
      await expect(page.getByRole('heading', { name: `メンバー管理：${communityName}` })).toBeVisible()
    })

    test('イベント作成：コミュニティを選ぶと、イベント作成の画面へ移る', async ({ page }) => {
      // 【何のため】コミュニティを選ぶ画面から、イベント作成の画面へ移れることを確かめる
      await sidebar(page).getByRole('link', { name: 'イベント作成' }).click()
      await expect(page.getByRole('heading', { name: 'イベント作成：コミュニティを選ぶ' })).toBeVisible()
      await main(page).getByRole('link', { name: communityName }).click()
      await expect(page).toHaveURL(`/manage/communities/${communityId}/events/new`)
      await expect(page.getByRole('heading', { name: 'イベントを作る' })).toBeVisible()
    })

    test('イベント編集：イベントを選ぶと、イベント編集の画面へ移る', async ({ page }) => {
      // 【何のため】イベントを選ぶ画面（S-19）から、編集の画面へ移れることを確かめる
      await sidebar(page).getByRole('link', { name: 'イベント編集' }).click()
      await expect(page.getByRole('heading', { name: 'イベント編集：イベントを選ぶ' })).toBeVisible()
      // 選ぶ画面には、どのコミュニティのイベントかも出る
      await expect(main(page).locator('li', { hasText: eventTitle }).getByText(`${communityName}（オーナー）`)).toBeVisible()
      await main(page).getByRole('link', { name: eventTitle }).click()
      await expect(page).toHaveURL(`/manage/communities/${communityId}/events/${eventId}/edit`)
      await expect(page.getByRole('heading', { name: 'イベントを編集' })).toBeVisible()
      await expect(page.getByLabel('タイトル')).toHaveValue(eventTitle)
    })

    test('申込者一覧：開催前・終了済みを切り替えられ、イベントを選ぶと申込者一覧へ移る', async ({ page }) => {
      // 【何のため】イベントを選ぶ画面から、申込者一覧へ移れることを確かめる
      await sidebar(page).getByRole('link', { name: '申込者一覧' }).click()
      await expect(page.getByRole('heading', { name: '申込者一覧：イベントを選ぶ' })).toBeVisible()
      // 「終了済み」に切り替えると、URL に status=past が入る。作ったイベントは開催前なので出ない
      await main(page).getByRole('link', { name: '終了済み' }).click()
      await expect(page).toHaveURL('/manage/select/applicants?status=past')
      await expect(main(page).getByRole('link', { name: eventTitle })).toHaveCount(0)
      await main(page).getByRole('link', { name: '開催前' }).click()

      await main(page).getByRole('link', { name: eventTitle }).click()
      await expect(page).toHaveURL(`/manage/communities/${communityId}/events/${eventId}/applicants`)
      await expect(page.getByRole('heading', { name: `申込者一覧：${eventTitle}` })).toBeVisible()
      await expect(main(page).getByText('まだ申込みはありません。')).toBeVisible()
    })
  })
})

test.describe('管理をしていない人（outsider）の画面', () => {
  test.use({ storageState: browserStatePath('outsider') })

  test('選ぶ画面には、何も出ない', async ({ page }) => {
    // 【何のため】管理をしていない人には、選べるコミュニティ・イベントがないことを確かめる
    const expectations: [string, string][] = [
      ['/manage/communities', 'オーナーか運営スタッフをしているコミュニティは、まだありません。'],
      ['/manage/select/community-edit', 'オーナーをしているコミュニティは、まだありません。'],
      ['/manage/select/members', 'オーナーか運営スタッフをしているコミュニティは、まだありません。'],
      ['/manage/select/event-create', 'イベントを作れるコミュニティ（オーナーか運営スタッフをしていて、アーカイブしていないもの）は、まだありません。'],
      ['/manage/select/event-edit', '開催前のイベントはありません。'],
      ['/manage/select/applicants', '開催前のイベントはありません。'],
    ]
    // for (const [a, b] of 配列)：配列の1組ずつを、a と b に分けて取り出す
    for (const [path, message] of expectations) {
      await page.goto(path)
      await expect(main(page).getByText(message)).toBeVisible()
      await expect(main(page).getByRole('link', { name: communityName })).toHaveCount(0)
      await expect(main(page).getByRole('link', { name: eventTitle })).toHaveCount(0)
    }
  })

  test('ほかの人のコミュニティ・イベントの管理の画面を開いても、「権限」の文が出る', async ({ page }) => {
    // 【何のため】URL を直接打ち込んでも、管理の画面の中身（申請・メンバー・申込者など）は見えないことを確かめる
    const base = `/manage/communities/${communityId}`
    const expectations: [string, string][] = [
      [`${base}/edit`, 'コミュニティを編集できるのは、オーナーだけです。'],
      [`${base}/members`, 'メンバー管理は、オーナーと運営スタッフだけが使えます。'],
      [`${base}/events/new`, 'イベントを作れるのは、オーナーと運営スタッフだけです。'],
      [`${base}/events/${eventId}/edit`, 'イベントを編集できるのは、オーナーと運営スタッフだけです。'],
      [`${base}/events/${eventId}/applicants`, '申込者一覧は、オーナーと運営スタッフだけが見られます。'],
    ]
    for (const [path, message] of expectations) {
      await page.goto(path)
      await expect(main(page).getByText(message)).toBeVisible()
      // 入力欄やボタンは出ない
      await expect(main(page).getByRole('button')).toHaveCount(0)
    }

    // コミュニティとイベントの詳細にも、管理の画面へのリンクは出ない
    await page.goto(`/communities/${communityId}`)
    await expect(main(page).getByRole('button', { name: '参加を申請する' })).toBeVisible()
    await expect(main(page).getByText('管理：')).toHaveCount(0)
    await page.goto(`/communities/${communityId}/events/${eventId}`)
    await expect(page.getByRole('heading', { name: eventTitle, level: 1 })).toBeVisible()
    await expect(main(page).getByText('管理：')).toHaveCount(0)
  })
})

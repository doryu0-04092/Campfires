/**
 * 【役割】スマートフォンの画面の幅（360px）で、主な画面が横にはみ出さないこと、メニューが使えることを確かめるテスト。
 * 【なぜ必要か】非機能要件（docs/non-functional-requirements.md）で「スマートフォン（幅 360px）でも、横にはみ出さずに使える」と決めている。
 * 横にはみ出すと、画面を左右に動かさないと読めず、ボタンが画面の外に出て押せなくなるため。
 *
 * 確かめる主な機能：
 * - 主な画面（ログインしていない人・メンバー・オーナーの画面）が、どれも横にはみ出さない
 * - 「メニュー」ボタンで左のメニューを開き、画面を移れる。管理メニュー（オーナー・運営スタッフ用）も開ける
 *
 * 画面の大きさ（360×740）とスマートフォンとしての設定は、playwright.config.ts の mobile プロジェクトで決めている。
 */
import { expect, test, type Page } from '@playwright/test'

import { browserStatePath } from '../../support/accounts'
import { mainScreens, openScreen, prepareScreenData, type ScreenData } from '../../support/screens'

// 準備で作ったデータ。beforeAll で入れる
let data: ScreenData

// 【何のため】ファイルの最初に1回だけ、画面に出すデータ（コミュニティ・イベントなど）を API で作る
test.beforeAll(async () => {
  data = await prepareScreenData('スマホ確認用')
})

/**
 * 【何のため】画面の幅からはみ出している部品を探して、その説明（タグの名前・クラス・文字）の一覧を返す。
 * はみ出していなければ、空の一覧（[]）を返す。どこがはみ出したかが分かるように、一覧にしている。
 */
async function findOverflow(page: Page): Promise<string[]> {
  // page.evaluate：ブラウザの中でプログラムを動かして、結果を受け取る
  return page.evaluate(() => {
    const width = document.documentElement.clientWidth
    /** はみ出しているか（大きさがない＝隠れている部品は、見えないので数えない。1px までの差は、計算の端数として許す） */
    const overflows = (element: Element) => {
      const rect = element.getBoundingClientRect()
      return rect.width > 0 && rect.height > 0 && (rect.right > width + 1 || rect.left < -1)
    }
    const found: string[] = []
    // querySelectorAll('body *')：body の中の、すべての部品
    for (const element of Array.from(document.querySelectorAll('body *'))) {
      // 親もはみ出しているときは、親だけを書く（同じ原因で、中の部品がたくさん並ばないように）
      if (!overflows(element) || (element.parentElement && overflows(element.parentElement))) continue
      const rect = element.getBoundingClientRect()
      const text = (element.textContent ?? '').trim().slice(0, 30)
      found.push(`<${element.tagName.toLowerCase()} class="${element.className}"> 左 ${Math.round(rect.left)} 右 ${Math.round(rect.right)}：${text}`)
    }
    return found
  })
}

/** 【何のため】ページ全体が、画面の幅より横に広くなっていないことを確かめる */
async function expectNoHorizontalScroll(page: Page) {
  // viewportSize()：playwright.config.ts で決めた画面の大きさ（幅 360px）
  const viewportWidth = page.viewportSize()!.width
  // scrollWidth：ページ全体の横幅（はみ出した部分も含む）。innerWidth：画面に見えている横幅
  const sizes = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }))
  // 先に、はみ出している部品を調べる（失敗したときに、どこを直せばよいかが分かるように）
  expect(await findOverflow(page), '画面の幅からはみ出している部品').toEqual([])
  expect(sizes.scrollWidth, 'ページ全体の横幅').toBeLessThanOrEqual(sizes.innerWidth)
  // スマートフォンのブラウザは、はみ出すと、画面を縮めて全体を見せることがある（innerWidth が広がる）。それも、はみ出しとみなす
  expect(sizes.innerWidth, '画面に見えている横幅').toBeLessThanOrEqual(viewportWidth)
}

// テストの名前に使う、開く人の呼び方
const WHO = { guest: 'ログインしていない人', member: 'メンバー', owner: 'オーナー' }

// 誰として開くかごとに、まとめて確かめる（test.describe の中の test.use は、その中のテストだけに効く）
for (const as of ['guest', 'member', 'owner'] as const) {
  test.describe(`${WHO[as]}の画面`, () => {
    // guest は Cookie なし。ほかは、準備（global-setup）で保存したログインの Cookie を使う
    test.use({ storageState: as === 'guest' ? { cookies: [], origins: [] } : browserStatePath(as) })

    // 画面の一覧は、データの番号が決まってから作るので、ここでは名前だけを使う（番号は 0 の仮の値）
    const names = mainScreens({ communityId: 0, communityName: '', eventId: 0, eventTitle: '' })
      .filter((s) => s.as === as)
      .map((s) => s.name)
    for (const name of names) {
      test(`幅 360px で「${name}」が横にはみ出さない`, async ({ page }) => {
        // 【何のため】スマートフォンで、この画面を左右に動かさずに読めて、ボタンを押せることを確かめる
        const screen = mainScreens(data).find((s) => s.as === as && s.name === name)!
        await openScreen(page, screen)
        await expectNoHorizontalScroll(page)
      })
    }
  })
}

test.describe('メニュー', () => {
  test.use({ storageState: browserStatePath('owner') })

  test('「メニュー」ボタンで左のメニューを開いて、画面を移れる', async ({ page }) => {
    // 【何のため】スマートフォンでは、左のメニューが折りたたまれている。ボタンで開き、指で押して移れることを確かめる
    await page.goto('/')
    const toggle = page.locator('aside').getByRole('button', { name: 'メニュー' })
    const listLink = page.locator('aside').getByRole('link', { name: 'コミュニティ一覧' })
    // 最初は閉じている
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await expect(listLink).toBeHidden()

    // tap()：指で押す操作（hasTouch の設定が必要）
    await toggle.tap()
    await expect(page.locator('aside').getByRole('button', { name: 'メニューを閉じる' })).toHaveAttribute('aria-expanded', 'true')
    await expect(listLink).toBeVisible()
    // メニューを開いた状態でも、横にはみ出さない
    await expectNoHorizontalScroll(page)

    await listLink.tap()
    await expect(page).toHaveURL('/communities')
    await expect(page.getByRole('heading', { name: 'コミュニティ一覧', level: 1 })).toBeVisible()
    // 項目を押すと、メニューは閉じる
    await expect(listLink).toBeHidden()
  })

  test('管理メニューを開いて、選ぶ画面を通って、メンバー管理の画面へ移れる', async ({ page }) => {
    // 【何のため】オーナーが、スマートフォンからも、管理の画面へたどり着けることを確かめる
    await page.goto('/')
    await page.locator('aside').getByRole('button', { name: 'メニュー' }).tap()
    const manageToggle = page.locator('aside').getByRole('button', { name: 'オーナー・運営スタッフ用' })
    // 管理メニューが閉じていれば開く（開け閉めの状態は、ブラウザに覚えられているため）
    if ((await manageToggle.getAttribute('aria-expanded')) === 'false') await manageToggle.tap()
    await expect(manageToggle).toHaveAttribute('aria-expanded', 'true')
    await expectNoHorizontalScroll(page)

    await page.locator('aside').getByRole('link', { name: 'メンバー管理', exact: true }).tap()
    await expect(page).toHaveURL('/manage/select/members')
    // 失敗してやり直したときに、同じ名前のコミュニティが増えることがあるので、URL（href）で、準備で作ったものを選ぶ
    await page.locator('main').locator(`a[href="/manage/communities/${data.communityId}/members"]`).tap()
    await expect(page).toHaveURL(`/manage/communities/${data.communityId}/members`)
    await expect(page.getByRole('heading', { name: `メンバー管理：${data.communityName}`, level: 1 })).toBeVisible()
    await expectNoHorizontalScroll(page)
  })

  test('画面上部のメニュー（お知らせ・アカウント設定・ログアウト）が、画面の中に収まって押せる', async ({ page }) => {
    // 【何のため】画面上部のメニューが、狭い画面で折り返されても、画面の外に出ず押せることを確かめる
    await page.goto('/')
    const header = page.locator('header')
    for (const item of [
      header.getByRole('link', { name: /お知らせ/ }),
      header.getByRole('link', { name: 'アカウント設定' }),
      header.getByRole('button', { name: 'ログアウト' }),
    ]) {
      // toBeInViewport：画面に見えている範囲の中にあること
      await expect(item).toBeInViewport()
    }
    await header.getByRole('link', { name: 'アカウント設定' }).tap()
    await expect(page.getByRole('heading', { name: 'アカウント設定', level: 1 })).toBeVisible()
  })
})

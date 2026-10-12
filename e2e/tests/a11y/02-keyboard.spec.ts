/**
 * 【役割】マウスを使わず、キーボードだけで、アプリを使えることを確かめるテスト。
 * 【なぜ必要か】WCAG 2.2 AA では「すべての操作を、キーボードだけでできる」「いま選んでいる場所（フォーカス）が目で見える」ことが決まっている。
 * 手の不自由な人や、読み上げソフトを使う人は、マウスではなくキーボード（Tab キーなど）で操作するため。
 * 自動の検査（axe、01-axe.spec.ts）では、実際にキーボードで操作できるかまでは分からないので、ここで確かめる。
 *
 * 使うキー：Tab（次の場所へ）、Shift+Tab（前の場所へ）、Enter（リンク・ボタンを押す）、Space（ボタン・チェックボックスを押す）
 *
 * ログインのテストでは、本当にログインする（本番の回数制限の、ログイン1回分を使う）。
 */
import { expect, test, type Locator, type Page } from '@playwright/test'

import { account, browserStatePath } from '../../support/accounts'

/**
 * 【何のため】Tab キーを押して、target にフォーカスが来るまで進める（キーボードだけで、そこへたどり着けることを確かめる）。
 * max 回押しても来なければ、失敗にする（キーボードでは、たどり着けないということ）。
 */
async function tabTo(page: Page, target: Locator, max = 40) {
  for (let i = 0; i < max; i++) {
    await page.keyboard.press('Tab')
    // evaluate：その部品について、ブラウザの中でプログラムを動かす。document.activeElement は、いまフォーカスがある部品
    if (await target.evaluate((element) => element === document.activeElement)) return
  }
  throw new Error(`Tab を ${max} 回押しても、${target} にフォーカスが来ませんでした`)
}

test.describe('ログインしていない人', () => {
  test.use({ storageState: { cookies: [], origins: [] } })

  test('キーボードだけで、ログインできる', async ({ page }) => {
    // 【何のため】マウスを使えない人も、ログインの画面で、入力・パスワードの表示・ログインができることを確かめる
    const acc = account('outsider')
    await page.goto('/login')
    await expect(page.getByRole('heading', { name: 'ログイン', level: 1 })).toBeVisible()

    const email = page.getByLabel('メールアドレス')
    const password = page.getByLabel('パスワード', { exact: true })
    const show = page.getByRole('checkbox', { name: 'パスワードを表示する' })
    const submit = page.getByRole('button', { name: 'ログイン' })

    // 画面の上から順に Tab で進み、メールアドレスの欄にたどり着く
    await tabTo(page, email)
    await expect(email).toBeFocused()
    // keyboard.type：キーボードで1文字ずつ打つ
    await page.keyboard.type(acc.email)
    await page.keyboard.press('Tab')
    await expect(password).toBeFocused()
    await page.keyboard.type(acc.password)

    // Shift+Tab で1つ戻ると、メールアドレスの欄に戻る（入力した値は消えない）
    await page.keyboard.press('Shift+Tab')
    await expect(email).toBeFocused()
    await expect(email).toHaveValue(acc.email)
    await page.keyboard.press('Tab')
    await expect(password).toBeFocused()

    // 「パスワードを表示する」は、Space で切り替えられる
    await page.keyboard.press('Tab')
    await expect(show).toBeFocused()
    await page.keyboard.press('Space')
    await expect(show).toBeChecked()
    await expect(password).toHaveAttribute('type', 'text')
    await page.keyboard.press('Space')
    await expect(password).toHaveAttribute('type', 'password')

    // ログインのボタンへ進み、Enter で押す
    await page.keyboard.press('Tab')
    await expect(submit).toBeFocused()
    await page.keyboard.press('Enter')

    await expect(page).toHaveURL('/')
    await expect(page.locator('header').getByText(`${acc.nickname} さん`)).toBeVisible()
  })
})

test.describe('オーナー', () => {
  test.use({ storageState: browserStatePath('owner') })

  test('キーボードだけで、左の管理メニューを開いて、管理の画面へ移れる', async ({ page }) => {
    // 【何のため】管理メニューの開け閉め（ボタン）と、その中のリンクを、キーボードだけで使えることを確かめる
    await page.goto('/')
    const toggle = page.locator('aside').getByRole('button', { name: 'オーナー・運営スタッフ用' })
    // 新しいブラウザでは、管理メニューは閉じている
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')

    await tabTo(page, toggle)
    // Enter でボタンを押すと、開く
    await page.keyboard.press('Enter')
    await expect(toggle).toHaveAttribute('aria-expanded', 'true')
    // Space でも押せる（閉じて、もう一度開く）
    await page.keyboard.press('Space')
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await page.keyboard.press('Space')
    await expect(toggle).toHaveAttribute('aria-expanded', 'true')

    // 開いたメニューの、最初の項目は、すぐ次（Tab 1回）にある
    await page.keyboard.press('Tab')
    await expect(page.locator('aside').getByRole('link', { name: '管理しているコミュニティ' })).toBeFocused()
    // Tab で「メンバー管理」まで進み、Enter で開く
    const members = page.locator('aside').getByRole('link', { name: 'メンバー管理', exact: true })
    await tabTo(page, members, 5)
    await page.keyboard.press('Enter')
    await expect(page).toHaveURL('/manage/select/members')
    await expect(page.getByRole('heading', { name: 'メンバー管理：コミュニティを選ぶ', level: 1 })).toBeVisible()

    // Shift+Tab で戻ると、ひとつ前のメニューの項目（コミュニティ編集）にフォーカスが移る
    await members.focus()
    await page.keyboard.press('Shift+Tab')
    await expect(page.locator('aside').getByRole('link', { name: 'コミュニティ編集', exact: true })).toBeFocused()
  })
})

/**
 * 【何のため】いまフォーカスがある部品に、目に見える印（枠線や影）が付いているかを調べる。
 * 付いていなければ、その部品の説明を返す（付いていれば null）。
 */
async function missingFocusIndicator(page: Page): Promise<string | null> {
  return page.evaluate(() => {
    const element = document.activeElement
    if (!element || element === document.body) return null
    // getComputedStyle：その部品に、最終的に効いている見た目（CSS）を読む
    const style = getComputedStyle(element)
    // 枠線（outline）が、なし（none）でなく、太さがあれば、印がある
    const hasOutline = style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) > 0
    // 影（box-shadow）で印を付ける作り方もある
    const hasShadow = style.boxShadow !== 'none'
    if (hasOutline || hasShadow) return null
    return `<${element.tagName.toLowerCase()}> ${(element.textContent ?? '').trim().slice(0, 20)}`
  })
}

// 確かめる画面（ログインしていない人の画面と、オーナーの入力の多い画面）
for (const { as, path } of [
  { as: 'guest', path: '/' },
  { as: 'guest', path: '/login' },
  { as: 'guest', path: '/signup' },
  { as: 'owner', path: '/manage/communities/new' },
  { as: 'owner', path: '/account' },
] as const) {
  test.describe(`${as === 'guest' ? 'ログインしていない人' : 'オーナー'}の ${path}`, () => {
    test.use({ storageState: as === 'guest' ? { cookies: [], origins: [] } : browserStatePath('owner') })

    test(`${path} で、Tab で選んだ部品に、目に見える印（フォーカスの枠）が付く`, async ({ page }) => {
      // 【何のため】キーボードで操作する人が、いまどこを選んでいるかを、目で見て分かることを確かめる
      await page.goto(path)
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
      const missing: string[] = []
      // 画面の上から 20 個の部品を、Tab で順に選んで調べる
      for (let i = 0; i < 20; i++) {
        await page.keyboard.press('Tab')
        const problem = await missingFocusIndicator(page)
        if (problem) missing.push(problem)
      }
      expect(missing).toEqual([])
    })
  })
}

/**
 * 【役割】主な画面が、アクセシビリティの決まり（WCAG 2.2 の AA）に合っているかを、自動の検査（axe）で確かめるテスト。
 * 【なぜ必要か】非機能要件（docs/non-functional-requirements.md）で「WCAG 2.2 AA に合わせる」と決めている。
 * 目の見えにくい人（読み上げソフトを使う人）や、色の見分けにくい人も使えるように、
 * 文字と背景の色の差（コントラスト）、入力欄の名前（ラベル）、見出しの順番、リンクの名前などを、画面ごとに確かめるため。
 *
 * axe（アクス）：Deque 社が作っている、アクセシビリティを自動で検査する道具。@axe-core/playwright で Playwright から使う。
 * 自動の検査で見つけられるのは、決まりの一部（目安として3〜5割）。キーボードだけで使えるかは、02-keyboard.spec.ts で確かめる。
 */
import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

import { browserStatePath } from '../../support/accounts'
import { mainScreens, openScreen, prepareScreenData, type ScreenData } from '../../support/screens'

// 確かめる決まりの範囲（axe の「タグ」）。WCAG 2.0・2.1・2.2 の、A と AA の決まり
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

// 開く人の呼び方（テストの名前に使う）
const WHO = { guest: 'ログインしていない人', member: 'メンバー', owner: 'オーナー' }

let data: ScreenData

// 【何のため】ファイルの最初に1回だけ、画面に出すデータ（コミュニティ・イベントなど）を API で作る
test.beforeAll(async () => {
  data = await prepareScreenData('アクセシビリティ確認用')
})

for (const as of ['guest', 'member', 'owner'] as const) {
  test.describe(`${WHO[as]}の画面`, () => {
    // guest は Cookie なし。ほかは、準備（global-setup）で保存したログインの Cookie を使う
    test.use({ storageState: as === 'guest' ? { cookies: [], origins: [] } : browserStatePath(as) })

    // 画面の一覧は、データの番号が決まってから作るので、ここでは名前だけを使う（番号は 0 の仮の値）
    const names = mainScreens({ communityId: 0, communityName: '', eventId: 0, eventTitle: '' })
      .filter((s) => s.as === as)
      .map((s) => s.name)
    for (const name of names) {
      test(`「${name}」に、WCAG 2.2 AA の違反がない`, async ({ page }) => {
        // 【何のため】この画面を、読み上げソフトの人・色の見分けにくい人も使えることを、自動の検査で確かめる
        const screen = mainScreens(data).find((s) => s.as === as && s.name === name)!
        await openScreen(page, screen)
        // new AxeBuilder({ page })：この画面を検査する準備。withTags で、確かめる決まりの範囲を決める
        const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()
        // 違反を「決まりの名前：説明（場所）」の一覧にする。失敗したときに、どこを直せばよいかが分かるように
        const violations = results.violations.map(
          (v) => `${v.id}（${v.impact}）：${v.help} → ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`,
        )
        expect(violations).toEqual([])
      })
    }
  })
}

test.describe('メニューを開いた状態', () => {
  test.use({ storageState: browserStatePath('owner') })

  test('管理メニュー（オーナー・運営スタッフ用）を開いた状態でも、違反がない', async ({ page }) => {
    // 【何のため】ふだんは閉じている管理メニューの中身（リンクと見出し）も、検査の対象にする
    await page.goto('/')
    await page.locator('aside').getByRole('button', { name: 'オーナー・運営スタッフ用' }).click()
    await expect(page.locator('aside').getByRole('link', { name: '申込者一覧' })).toBeVisible()
    const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()
    expect(results.violations.map((v) => `${v.id}：${v.help}`)).toEqual([])
  })
})

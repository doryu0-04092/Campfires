/**
 * 【役割】パスワードを忘れた場合の画面（S-20「パスワードを忘れた場合」、S-21「パスワードの再設定」）を、画面から確かめるテスト。
 * 【なぜ必要か】本番には、メールを送る仕組みがまだないので、パスワードの再設定を止めている（API は 503）。
 * その間、利用者が迷わないように、リンクを隠し、問い合わせを案内できていることを確かめる。
 * また、再設定のリンクを開いたとき、URL からトークン（秘密の値）が消えることも確かめる（履歴や Referer から漏れないように）。
 *
 * ログインしていない状態で確かめるので、ログインの回数は使わない。再設定の依頼の API も呼ばない（止めているので、画面が入力欄を出さない）。
 * それでも、本番で再設定を始めたときに、依頼の回数制限（1時間に5回）を使いすぎないように、Edge（browser）だけで行う
 * （playwright.config.ts の testIgnore）。
 *
 * メールの送信を始めて、本番で再設定を使えるようにしたら、このファイルを書き直す。
 */
import { expect, test } from '@playwright/test'

import { main } from './helpers'

test.describe('パスワードを忘れた場合（再設定を止めているとき）', () => {
  test('ログインの画面には、リンクではなく、問い合わせの案内が出る', async ({ page }) => {
    // 【何のため】使える機能の一覧（GET /api/auth/features）を見て、「パスワードを忘れた場合」のリンクを隠すことを確かめる。
    // waitForResponse：その API の返事が届くまで待つ（画面を開く前に、待ち始めておく）
    const features = page.waitForResponse('**/api/auth/features')
    await page.goto('/login')
    expect((await (await features).json()).passwordResetEnabled).toBe(false)

    await expect(main(page).getByText('パスワードを忘れた場合は、お問い合わせください。')).toBeVisible()
    await expect(main(page).getByRole('link', { name: 'パスワードを忘れた場合' })).toHaveCount(0)
  })

  test('「パスワードを忘れた場合」の画面を開くと、使えないことと、問い合わせ先が案内される', async ({ page }) => {
    // 【何のため】URL を直接開いた人にも、入力欄ではなく、案内を出すことを確かめる
    await page.goto('/password/forgot')
    await expect(page.getByRole('heading', { name: 'パスワードを忘れた場合' })).toBeVisible()
    const alert = main(page).getByRole('alert')
    await expect(alert).toContainText('パスワードの再設定はご利用いただけません')
    await expect(alert.getByRole('link', { name: 'プライバシーポリシー' })).toBeVisible()
    await expect(page.getByLabel('メールアドレス')).toHaveCount(0)
  })

  test('再設定のリンクを開くと、URL からトークンが消え、送ると使えないことが案内される', async ({ page }) => {
    // 【何のため】リンクのトークンを、URL（履歴）に残さないことと、止めているときの 503 を画面で案内することを確かめる
    await page.goto('/password/reset#token=e2e-dummy-token')
    await expect(page.getByRole('heading', { name: 'パスワードの再設定' })).toBeVisible()
    // toHaveURL に正規表現を渡すと、URL がその形かを確かめる。$ は「ここで終わる」（# 以降が付いていない）
    await expect(page).toHaveURL(/\/password\/reset$/)

    await page.getByLabel('新しいパスワード', { exact: true }).fill('e2e-new-password-1')
    await page.getByLabel('新しいパスワード（確認）', { exact: true }).fill('e2e-new-password-1')
    await page.getByRole('button', { name: 'パスワードを再設定する' }).click()
    await expect(main(page).getByRole('alert')).toContainText('パスワードの再設定はご利用いただけません')
  })
})

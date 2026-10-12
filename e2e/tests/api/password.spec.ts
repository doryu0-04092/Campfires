/**
 * 【役割】パスワードの変更（F-24）と、パスワードの再設定（F-25）を、外から HTTP で確かめる E2E テスト。
 * 【なぜ必要か】本番の設定（セッションをデータベースに置く Spring Session JDBC）で、パスワードを変えると
 * ほかの端末のログインが止まり、操作した端末はログインしたままになることを、本物の通り道で確かめるため。
 * また、本番にはメールを送る仕組みがまだないので、再設定が止まっている（503）ことも確かめる。
 *
 * パスワードの変更は、outsider で行い、最後に元のパスワードに戻す。
 * パスワードを変えると、それまでのログインの Cookie は使えなくなるので、戻した後の新しい Cookie を保存し直す
 * （あとのテスト・画面のテスト・後片付けが、そのまま使えるように）。
 * 本番の回数制限：ログインを1回（ほかの端末の代わり）、再設定の依頼を1回使う（e2e/README.md の「回数制限の内訳」）。
 */
import crypto from 'node:crypto'

import { expect, test } from '@playwright/test'

import { ApiSession, json } from '../../support/api'
import { account, saveSession, sessionOf } from '../../support/accounts'

test.describe.serial('パスワードの変更', () => {
  // 【何のため】パスワードを変えると、ほかの端末のログインは使えなくなり、操作した端末は新しいセッションで続けられることを確かめる。
  // 最後に元のパスワードに戻し、新しいログインの Cookie を保存し直す
  test('変更すると、ほかの端末のログインは使えなくなる', async () => {
    const acc = account('outsider')
    // 一時的に使う、新しいパスワード（実行ごとにランダム。半角の英字・数字・記号だけ）
    const temporary = `${crypto.randomBytes(18).toString('base64url')}-1`

    // 端末1：準備で保存したログインの Cookie。端末2：ログインし直して、別のセッションを作る（ほかの端末の代わり）
    const device1 = await sessionOf('outsider')
    const device2 = await ApiSession.create()
    await json(await device2.post('/api/auth/login', { email: acc.email, password: acc.password }))
    try {
      // 今のパスワードが違えば、変えられない（currentPassword の項目のエラー）
      const wrong = await device1.put('/api/me/password', { currentPassword: 'wrong-password', newPassword: temporary })
      expect(wrong.status()).toBe(400)
      const wrongBody = await wrong.json()
      expect(wrongBody.code).toBe('INVALID_PASSWORD')
      expect(wrongBody.errors[0].field).toBe('currentPassword')

      // 新しいパスワードがルールに合わなければ、変えられない（アカウントの登録と同じルール）
      const short = await device1.put('/api/me/password', { currentPassword: acc.password, newPassword: 'short' })
      expect(short.status()).toBe(400)
      expect((await short.json()).errors[0].field).toBe('newPassword')

      // 端末1で変える。サーバーは、端末1のセッションを作り直す（新しい Cookie が返り、APIRequestContext が覚える）
      const changed = await device1.put('/api/me/password', { currentPassword: acc.password, newPassword: temporary })
      expect(changed.status()).toBe(204)
      expect((await device1.get('/api/me')).status()).toBe(200)
      // 端末2（変える前のセッション）は、もう使えない
      expect((await device2.get('/api/me')).status()).toBe(401)

      // 元のパスワードに戻す（a11y のキーボードのテストと後片付けが、元のパスワードでログインするため）
      const restored = await device1.put('/api/me/password', { currentPassword: temporary, newPassword: acc.password })
      expect(restored.status()).toBe(204)
      expect((await device1.get('/api/me')).status()).toBe(200)
      // 準備で保存した Cookie は、もう使えないので、新しい Cookie を保存し直す
      await saveSession(device1, 'outsider')
    } finally {
      await device1.dispose()
      await device2.dispose()
    }
  })

  // 【何のため】ログインしていなければ、パスワードを変えられない（401）ことを確かめる
  test('ログインしていなければ 401', async () => {
    const guest = await ApiSession.create()
    const response = await guest.put('/api/me/password', { currentPassword: 'x', newPassword: 'new-password-1' })
    expect(response.status()).toBe(401)
    await guest.dispose()
  })
})

test.describe('パスワードの再設定', () => {
  // 【何のため】本番の設定では、メールを送る仕組みがないので、再設定を止めていることを確かめる。
  // 画面は、これを見て「パスワードを忘れた場合」のリンクを隠す。
  // メールの送信を始めて、本番で再設定を使えるようにしたら、このテストを書き直す
  test('使える機能の一覧で、再設定を止めていると分かる', async ({ request }) => {
    const response = await request.get('/api/auth/features')
    expect(response.status()).toBe(200)
    expect(await response.json()).toEqual({ passwordResetEnabled: false })
  })

  // 【何のため】止めている間は、再設定の依頼も、再設定も、503（PASSWORD_RESET_UNAVAILABLE）になることを確かめる。
  // 登録されているメールアドレスでも同じ（トークンは作られない）
  test('依頼も再設定も 503', async () => {
    const guest = await ApiSession.create()
    try {
      const requested = await guest.post('/api/auth/password-reset-requests', { email: account('outsider').email })
      expect(requested.status()).toBe(503)
      expect((await requested.json()).code).toBe('PASSWORD_RESET_UNAVAILABLE')

      const reset = await guest.post('/api/auth/password-resets', {
        token: crypto.randomBytes(32).toString('base64url'),
        newPassword: 'new-password-123',
      })
      expect(reset.status()).toBe(503)
      expect((await reset.json()).code).toBe('PASSWORD_RESET_UNAVAILABLE')
    } finally {
      await guest.dispose()
    }
  })
})

/**
 * 【役割】アカウントの機能（自分の情報、ニックネームの変更、ログアウトとログイン、ログインの失敗）を、外から確かめる E2E テスト。
 * 【なぜ必要か】本番の設定（セッションをデータベースに置く Spring Session JDBC、Cookie の設定）で、
 * ログインの状態が正しく作られ、消えることを確かめるため。
 *
 * ログインのロック（5回の失敗）は、本番の利用者に影響しないように、手元だけで確かめる（local-only.spec.ts）。
 */
import { expect, test } from '@playwright/test'

import { ApiSession, json } from '../../support/api'
import { account, saveSession, sessionOf } from '../../support/accounts'

test.describe.serial('アカウント', () => {
  // 【何のため】自分の情報（本名、メールアドレスを含む）は、本人だけが見られることを確かめる
  test('自分の情報を見られる', async () => {
    const outsider = await sessionOf('outsider')
    const me = await json<{ id: number; email: string; realName: string }>(await outsider.get('/api/me'))
    expect(me.id).toBe(account('outsider').id)
    expect(me.email).toBe(account('outsider').email)
    expect(me.realName).toBe(account('outsider').realName)
    await outsider.dispose()
  })

  // 【何のため】ニックネームを変えられ、前後の空白が取り除かれることを確かめる
  test('ニックネームを変える', async () => {
    const outsider = await sessionOf('outsider')
    const updated = await json<{ nickname: string }>(await outsider.patch('/api/me', { nickname: '  e2e-変更後  ' }))
    expect(updated.nickname).toBe('e2e-変更後')
    // 長すぎるニックネームは断られる
    expect((await outsider.patch('/api/me', { nickname: 'あ'.repeat(31) })).status()).toBe(400)
    await json(await outsider.patch('/api/me', { nickname: account('outsider').nickname }))
    await outsider.dispose()
  })

  // 【何のため】ログアウトすると、同じ Cookie では、もう操作できないことを確かめる（セッションがサーバーで消える）
  test('ログアウトすると、同じ Cookie では操作できない', async () => {
    const outsider = await sessionOf('outsider')
    // ログアウトの前に、Cookie を取っておく
    const cookiesBefore = (await outsider.context.storageState()).cookies
    expect((await outsider.post('/api/auth/logout')).status()).toBe(204)
    expect((await outsider.get('/api/me')).status()).toBe(401)
    await outsider.dispose()

    // 取っておいた（ログアウトの前の）Cookie を、もう一度使っても、ログインした状態には戻らない
    const replay = await ApiSession.create()
    await replay.context.storageState()
    const reused = await replay.context.get('/api/me', {
      headers: { Cookie: cookiesBefore.map((c) => `${c.name}=${c.value}`).join('; ') },
    })
    expect(reused.status()).toBe(401)
    await replay.dispose()
  })

  // 【何のため】正しいパスワードでログインでき、違うパスワードでは、理由を言わずに断られることを確かめる
  test('ログインする', async () => {
    const acc = account('outsider')
    const session = await ApiSession.create()
    const wrong = await session.post('/api/auth/login', { email: acc.email, password: 'wrong-password' })
    expect(wrong.status()).toBe(401)
    expect((await wrong.json()).code).toBe('INVALID_CREDENTIALS')

    // メールアドレスは、大文字と小文字を区別しない
    const me = await json<{ id: number }>(
      await session.post('/api/auth/login', { email: acc.email.toUpperCase(), password: acc.password }),
    )
    expect(me.id).toBe(acc.id)
    expect((await session.get('/api/me')).status()).toBe(200)
    // あとのテスト（画面のテスト）のために、新しいログインの Cookie を保存しておく
    await saveSession(session, 'outsider')
    await session.dispose()
  })

  // 【何のため】同じメールアドレスでは、もう一度登録できないことを確かめる
  test('登録済みのメールアドレスでは登録できない（409）', async () => {
    const session = await ApiSession.create()
    const acc = account('member')
    const response = await session.post('/api/auth/signup', {
      realName: 'x',
      nickname: 'x',
      email: acc.email,
      password: 'another-password-123',
      agreedToPolicies: true,
    })
    expect(response.status()).toBe(409)
    await session.dispose()
  })
})

/**
 * 【役割】手元の箱でだけ行う E2E テスト（ログインのロック）。
 * 【なぜ必要か】ログインに5回失敗すると、そのメールアドレスが15分ロックされることを、本番と同じ箱で確かめるため。
 * 本番で行うと、本番の回数制限（同じ IP アドレスからのログイン）を使ってしまうので、本番では行わない（test.skip）。
 */
import crypto from 'node:crypto'

import { expect, test } from '@playwright/test'

import { ApiSession } from '../../support/api'
import { IS_LOCAL } from '../../support/env'

// 手元の箱でなければ、このファイルのテストは行わない
test.skip(!IS_LOCAL, '手元の箱だけで行うテスト')

// 【何のため】5回続けて失敗すると、6回目は 429 になり、何秒後に試せるかが返ることを確かめる
test('ログインに5回失敗すると、ロックされる', async () => {
  // 登録されていないメールアドレスでも、同じようにロックされる（登録の有無を探られないようにするため）
  const email = `e2e-lock-${crypto.randomUUID()}@example.com`
  const session = await ApiSession.create()
  for (let i = 0; i < 5; i++) {
    expect((await session.post('/api/auth/login', { email, password: 'wrong-password' })).status()).toBe(401)
  }
  const locked = await session.post('/api/auth/login', { email, password: 'wrong-password' })
  expect(locked.status()).toBe(429)
  expect((await locked.json()).code).toBe('ACCOUNT_LOCKED')
  expect(Number(locked.headers()['retry-after'])).toBeGreaterThan(0)
  await session.dispose()
})

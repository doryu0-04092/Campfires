/**
 * 【役割】API の土台（死活の確認、セキュリティのヘッダー、CSRF、ログインのチェック、エラーの形、送れる大きさ）を、
 * 外から HTTP で確かめる E2E テスト。
 * 【なぜ必要か】自動テスト（./mvnw test）は、アプリの中だけで確かめる。本番では、その前にロードバランサーや
 * 本番の設定（application-prod.properties）があるので、本物の通り道で、守りが効いているかを確かめるため。
 */
import crypto from 'node:crypto'

import { expect, test } from '@playwright/test'

import { ApiSession } from '../../support/api'
import { sessionOf } from '../../support/accounts'
import { IS_HTTPS } from '../../support/env'

test.describe('API の土台', () => {
  // 【何のため】アプリと、データベースへの接続が動いていることを確かめる
  test('死活の確認が ok を返す', async ({ request }) => {
    const response = await request.get('/api/health')
    expect(response.status()).toBe(200)
    expect(await response.json()).toEqual({ status: 'ok' })
  })

  // 【何のため】画面（HTML）と API の返事に、ブラウザの守りのヘッダーが付いていることを確かめる
  test('セキュリティのヘッダーが付いている', async ({ request }) => {
    for (const path of ['/', '/api/communities']) {
      const headers = (await request.get(path)).headers()
      expect(headers['content-security-policy'], path).toContain("default-src 'self'")
      expect(headers['x-content-type-options'], path).toBe('nosniff')
      expect(headers['x-frame-options'], path).toBe('DENY')
      expect(headers['referrer-policy'], path).toBeTruthy()
      // サーバーのソフトの名前と版を、外に教えない
      expect(headers['server'], path).toBeUndefined()
      // HSTS（次からは HTTPS だけで来るように、という指示）は、HTTPS のときだけ返る
      if (IS_HTTPS) {
        expect(headers['strict-transport-security'], path).toContain('max-age=')
      }
    }
  })

  // 【何のため】HEAD のリクエスト（中身なしで、ヘッダーだけを返す）にも、守りのヘッダーが付くことを確かめる
  // （以前は付かなかった。SecurityConfig で、ヘッダーを処理の前に書くようにして直した）
  test('HEAD のリクエストにも、セキュリティのヘッダーが付く', async ({ request }) => {
    for (const path of ['/', '/api/communities', '/api/me']) {
      const headers = (await request.head(path)).headers()
      expect(headers['content-security-policy'], path).toContain("default-src 'self'")
      expect(headers['x-frame-options'], path).toBe('DENY')
      expect(headers['x-content-type-options'], path).toBe('nosniff')
    }
  })

  // 【何のため】ログインしていないと、自分の情報を見られないことを確かめる
  test('ログインしていなければ 401', async ({ request }) => {
    const response = await request.get('/api/me')
    expect(response.status()).toBe(401)
    expect((await response.json()).code).toBe('UNAUTHENTICATED')
  })

  // 【何のため】CSRF トークンを付けずに、データを変える API を呼ぶと、断られることを確かめる（ほかのサイトからの、なりすましの対策）
  test('CSRF トークンがなければ 403', async () => {
    const owner = await sessionOf('owner')
    const response = await owner.context.patch('/api/me', { data: { nickname: 'csrf-test' } })
    expect(response.status()).toBe(403)
    await owner.dispose()
  })

  // 【何のため】ほかのサイトの画面から、API の返事を読めないことを確かめる（CORS の許可を返さない）
  test('ほかのサイトからの通信に、読む許可を返さない', async ({ request }) => {
    const response = await request.get('/api/communities', { headers: { Origin: 'https://evil.example.com' } })
    expect(response.headers()['access-control-allow-origin']).toBeUndefined()
  })

  // 【何のため】エラーの返事が、決めた形（Problem Details と code）で、内部の情報を含まないことを確かめる
  test('エラーの返事は、決めた形で返る', async ({ request }) => {
    const notFound = await request.get('/api/communities/999999999')
    expect(notFound.status()).toBe(404)
    expect(notFound.headers()['content-type']).toContain('application/problem+json')
    expect((await notFound.json()).code).toBeTruthy()

    // 番号でない値は、見つからない扱い（404）か、入力のエラー（400）になる
    const badType = await request.get('/api/communities/abc')
    expect([400, 404]).toContain(badType.status())
    const text = await badType.text()
    // Java のクラスの名前や、例外の中身が出ていない
    expect(text).not.toMatch(/java\.|Exception|springframework/)
  })

  // 【何のため】入力のチェックで、項目ごとのエラーが返ることを確かめる
  test('入力のエラーは 400 と項目ごとの理由', async () => {
    const owner = await sessionOf('owner')
    const response = await owner.post('/api/communities', { name: '', description: 'x' })
    expect(response.status()).toBe(400)
    const body = await response.json()
    expect(body.code).toBe('VALIDATION_ERROR')
    expect(body.errors.map((e: { field: string }) => e.field)).toContain('name')
    await owner.dispose()
  })

  // 【何のため】大きすぎるデータは、中身を読む前に断られることを確かめる（64KB まで）
  test('大きすぎるデータは 413', async () => {
    const owner = await sessionOf('owner')
    const response = await owner.post('/api/communities', { name: 'x', description: 'あ'.repeat(40_000) })
    expect(response.status()).toBe(413)
    await owner.dispose()
  })

  // 【何のため】ログインのセッションの Cookie に、決めた守り（HttpOnly、SameSite=Lax、Secure）が付いていることを確かめる
  test('ログインの Cookie に守りが付いている', async () => {
    const session = await ApiSession.create()
    // 登録されていないメールアドレスでログインして、失敗の返事を見る（Cookie は、成功したときだけ作られる）
    // メールアドレスは、実行ごとに変える（同じアドレスで失敗を続けると、5回目でロックされ、何度も動かすうちに 429 になるため）
    const failed = await session.post('/api/auth/login', { email: `nobody-${crypto.randomUUID()}@example.com`, password: 'wrong-password' })
    expect(failed.status()).toBe(401)
    await session.dispose()

    // 準備（global-setup）で登録したときに受け取った Cookie を見る
    const owner = await sessionOf('owner')
    const cookies = (await owner.context.storageState()).cookies
    const sessionCookie = cookies.find((c) => c.name === 'JSESSIONID')
    expect(sessionCookie, 'JSESSIONID').toBeDefined()
    expect(sessionCookie!.httpOnly).toBe(true)
    expect(sessionCookie!.sameSite).toBe('Lax')
    expect(sessionCookie!.secure).toBe(true)
    await owner.dispose()
  })
})

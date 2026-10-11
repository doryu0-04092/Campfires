/**
 * 【役割】ログインとアカウントの偽の API（docs/api/openapi.yaml の auth と account）。
 * 【なぜ必要か】バックエンドができるまでの間、ログイン・ログアウトの画面の動きを確かめるため。
 * 今は、CSRF トークン、ログイン、ログアウト、自分の情報の取得だけを作っている。
 * 登録、ニックネームの変更、アカウント削除は、アカウントの画面を作るときに足す。
 */

import { delay, http, HttpResponse } from 'msw'
import type { FieldError } from '../../types/api'
import type { LoginRequest } from '../../types/account'
import { db } from '../db'
import {
  checkCsrf,
  clearSessionCookie,
  CSRF_COOKIE,
  findLoggedInUser,
  problem,
  sessionCookie,
  toMe,
} from './common'

// ログインの試行制限：5回続けて失敗したら、15分間ロックする（非機能要件 3章）
const MAX_LOGIN_FAILURES = 5
const LOCK_MILLISECONDS = 15 * 60 * 1000

export const authHandlers = [
  /**
   * CSRF トークンを受け取る（GET /api/auth/csrf）。
   * ランダムな合言葉を作り、XSRF-TOKEN の Cookie で渡す。
   * この Cookie は、画面のプログラムが読んでヘッダーに入れる必要があるので、HttpOnly にしない（本物も同じ）。
   */
  http.get('/api/auth/csrf', () => {
    // crypto.randomUUID() は、ブラウザに備わっている「推測できないランダムな文字」を作る関数
    const token = crypto.randomUUID()
    return new HttpResponse(null, {
      status: 204,
      headers: { 'Set-Cookie': `${CSRF_COOKIE}=${token}; Path=/; SameSite=Lax` },
    })
  }),

  /**
   * ログインする（POST /api/auth/login）。
   */
  http.post('/api/auth/login', async ({ request, cookies }) => {
    await delay(300)

    // CSRF トークンを確かめる。正しくなければ、その返事（403）をそのまま返す
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }

    // 送られてきたデータを読み取る。形が正しいとは限らないので、Partial（すべての項目が無いかもしれない形）として扱う
    const body = (await request.json()) as Partial<LoginRequest>
    const email = typeof body.email === 'string' ? body.email.trim() : ''
    const password = typeof body.password === 'string' ? body.password : ''

    // 入力のチェック：空の項目があれば 400
    const errors: FieldError[] = []
    if (email === '') {
      errors.push({ field: 'email', message: 'メールアドレスを入力してください。' })
    }
    if (password === '') {
      errors.push({ field: 'password', message: 'パスワードを入力してください。' })
    }
    if (errors.length > 0) {
      return problem(400, 'VALIDATION_ERROR', '入力内容に誤りがあります。', { errors })
    }

    // メールアドレスは、大文字と小文字を区別せずに扱う
    const key = email.toLowerCase()
    const now = Date.now()
    const failure = db.loginFailures.get(key)

    // ロック中なら 429。あと何秒で試せるかを Retry-After で返す
    if (failure?.lockedUntil && failure.lockedUntil > now) {
      const retryAfter = Math.ceil((failure.lockedUntil - now) / 1000)
      return problem(429, 'ACCOUNT_LOCKED', 'ログインに続けて失敗したため、しばらくログインできません。15分ほど待ってから、もう一度お試しください。', {
        headers: { 'Retry-After': String(retryAfter) },
      })
    }

    const user = db.users.find((u) => u.email.toLowerCase() === key)

    // メールアドレスかパスワードが違うとき。どちらが違うかは教えない（docs/api/openapi.yaml の login）
    if (!user || user.password !== password) {
      const count = (failure && failure.lockedUntil === null ? failure.count : 0) + 1
      db.loginFailures.set(key, {
        count,
        // 5回目の失敗で、15分間ロックする
        lockedUntil: count >= MAX_LOGIN_FAILURES ? now + LOCK_MILLISECONDS : null,
      })
      return problem(401, 'INVALID_CREDENTIALS', 'メールアドレスまたはパスワードが正しくありません。')
    }

    // 成功したら、失敗の記録を消し、ログインの証明の Cookie を渡す
    db.loginFailures.delete(key)
    return HttpResponse.json(toMe(user), {
      status: 200,
      headers: { 'Set-Cookie': sessionCookie(user.id) },
    })
  }),

  /**
   * ログアウトする（POST /api/auth/logout）。ログインしていなくても成功（204）を返す。
   */
  http.post('/api/auth/logout', async ({ request, cookies }) => {
    await delay(200)
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    return new HttpResponse(null, {
      status: 204,
      headers: { 'Set-Cookie': clearSessionCookie() },
    })
  }),

  /**
   * 自分の情報を取得する（GET /api/me）。ログインしていなければ 401。
   */
  http.get('/api/me', async ({ cookies }) => {
    await delay(200)
    const user = findLoggedInUser(cookies)
    if (!user) {
      return problem(401, 'UNAUTHENTICATED', 'ログインしてください。')
    }
    return HttpResponse.json(toMe(user))
  }),
]

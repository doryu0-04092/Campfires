/**
 * 【役割】アカウント登録、ログイン、ログアウトの偽の API（docs/api/openapi.yaml の auth）。
 * 【なぜ必要か】バックエンドができるまでの間、登録・ログイン・ログアウトの画面の動きを確かめるため。
 * 自分のアカウントの API（/api/me）は account.ts に分けている。
 */

import { delay, http, HttpResponse } from 'msw'
import type { FieldError } from '../../types/api'
import type { LoginRequest, SignupRequest } from '../../types/account'
import { db } from '../db'
import { checkCsrf, clearSessionCookie, CSRF_COOKIE, problem, sessionCookie, toMe } from './common'
import { Validator } from './validation'

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
   * アカウントを登録する（POST /api/auth/signup）。登録に成功したら、そのままログインした状態にする。
   */
  http.post('/api/auth/signup', async ({ request, cookies }) => {
    await delay(300)
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }

    const body = (await request.json()) as Partial<SignupRequest>

    // 入力のチェック（docs/api/openapi.yaml の SignupRequest のルール）
    const validator = new Validator()
    const realName = validator.text('realName', body.realName, '本名', 1, 50)
    const nickname = validator.text('nickname', body.nickname, 'ニックネーム', 1, 30)
    const email = validator.email('email', body.email)
    const password = validator.password('password', body.password)
    if (body.agreedToPolicies !== true) {
      validator.errors.push({ field: 'agreedToPolicies', message: 'プライバシーポリシーと利用規約に同意してください。' })
    }
    if (validator.hasErrors()) {
      return problem(400, 'VALIDATION_ERROR', '入力内容に誤りがあります。', { errors: validator.errors })
    }

    // すでに使われているメールアドレスなら 409（大文字と小文字は区別しない）
    const exists = db.users.some((u) => u.deletedAt === null && u.email.toLowerCase() === email.toLowerCase())
    if (exists) {
      return problem(409, 'EMAIL_ALREADY_REGISTERED', 'このメールアドレスは、すでに登録されています。')
    }

    // 新しいユーザーを作る。番号は、今いちばん大きい番号の次にする
    const now = new Date().toISOString()
    const user = {
      id: Math.max(...db.users.map((u) => u.id)) + 1,
      realName,
      nickname,
      email,
      password,
      // 同意した日時を記録する（本物では、同意した規約のバージョンも記録する）
      policiesAgreedAt: now,
      deletedAt: null,
      createdAt: now,
    }
    db.users.push(user)

    // 登録に成功したら、そのままログインした状態にする
    return HttpResponse.json(toMe(user), {
      status: 201,
      headers: { 'Set-Cookie': sessionCookie(user.id) },
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

    // アカウントを削除したユーザーは、メールアドレスが空になっているので見つからない
    const user = db.users.find((u) => u.deletedAt === null && u.email.toLowerCase() === key)

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

]

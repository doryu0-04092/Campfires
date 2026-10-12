/**
 * 【役割】使える機能の一覧と、パスワードの再設定の偽の API（docs/api/openapi.yaml の auth の
 * features、password-reset-requests、password-resets）。
 * 【なぜ必要か】バックエンドなしで（npm run dev）、「パスワードを忘れた場合」（S-20）と「パスワードの再設定」（S-21）の
 * 画面の動きを確かめるため。
 *
 * メールは送れないので、再設定のリンクは、ブラウザの開発者ツールのコンソールに表示する（本物の dev のプロファイルと同じ考え方）。
 * リンクを開くとページ全体を読み込み直すので、トークンだけは sessionStorage にも保存する（src/mocks/db/index.ts）。
 */

import { delay, http, HttpResponse } from 'msw'
import type { PasswordResetConfirmRequest, PasswordResetRequest } from '../../types/account'
import { db, savePasswordResetTokens } from '../db'
import { checkCsrf, problem } from './common'
import { Validator } from './validation'

// 再設定を使えるかどうか。false にすると、本番と同じ「使えない」（503）ときの画面を試せる
const MOCK_PASSWORD_RESET_ENABLED = true
// トークンの有効期限（30分）
const TOKEN_VALID_MILLISECONDS = 30 * 60 * 1000

// 依頼の返事の文（本物の PasswordResetController と同じ）
const ACCEPTED_MESSAGE =
  '登録されているメールアドレスなら、パスワードの再設定のメールを送りました。メールのリンクから、30分以内に再設定してください。'

/**
 * 【何のため】再設定を止めているときの返事（503）を作る。使えるときは null。
 */
function unavailable() {
  if (MOCK_PASSWORD_RESET_ENABLED) {
    return null
  }
  return problem(503, 'PASSWORD_RESET_UNAVAILABLE', '現在、パスワードの再設定はご利用いただけません。お手数ですが、お問い合わせください。')
}

/**
 * 【何のため】推測できないトークンを作る（32バイトの乱数を、URL に入れられる文字にする。本物と同じ考え方）。
 * crypto.getRandomValues は、ブラウザに備わっている、暗号に使える強さの乱数を作る関数。
 */
function newToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32))
  // btoa で Base64 にし、URL で特別な意味を持つ文字（+ / =）を、- _ に置き換える（= は取り除く）
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export const passwordResetHandlers = [
  /**
   * 今使える機能の一覧（GET /api/auth/features）。ログインしていなくても呼べる。
   */
  http.get('/api/auth/features', async () => {
    await delay(100)
    return HttpResponse.json({ passwordResetEnabled: MOCK_PASSWORD_RESET_ENABLED })
  }),

  /**
   * パスワードの再設定を依頼する（POST /api/auth/password-reset-requests）。
   * 登録されていても、いなくても、同じ返事（202）にする。
   */
  http.post('/api/auth/password-reset-requests', async ({ request, cookies }) => {
    await delay(300)
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const disabled = unavailable()
    if (disabled) {
      return disabled
    }

    const body = (await request.json()) as Partial<PasswordResetRequest>
    const validator = new Validator()
    const email = validator.email('email', body.email)
    if (validator.hasErrors()) {
      return problem(400, 'VALIDATION_ERROR', '入力内容に誤りがあります。', { errors: validator.errors })
    }

    const user = db.users.find((u) => u.deletedAt === null && u.email.toLowerCase() === email.toLowerCase())
    if (user) {
      // 前に作った、まだ使っていないトークンは、使えなくする
      for (const saved of db.passwordResetTokens.values()) {
        if (saved.userId === user.id && saved.usedAt === null) {
          saved.usedAt = Date.now()
        }
      }
      const token = newToken()
      db.passwordResetTokens.set(token, { userId: user.id, expiresAt: Date.now() + TOKEN_VALID_MILLISECONDS, usedAt: null })
      savePasswordResetTokens()
      // メールの代わりに、リンクをコンソールに表示する（開発中だけ。本物の本番では、絶対にリンクを表示しない）
      console.info(`【偽の API】パスワードの再設定のリンク: ${window.location.origin}/password/reset#token=${token}`)
    }
    return HttpResponse.json({ message: ACCEPTED_MESSAGE }, { status: 202 })
  }),

  /**
   * リンクのトークンと新しいパスワードで、パスワードを決め直す（POST /api/auth/password-resets）。
   */
  http.post('/api/auth/password-resets', async ({ request, cookies }) => {
    await delay(300)
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const disabled = unavailable()
    if (disabled) {
      return disabled
    }

    const body = (await request.json()) as Partial<PasswordResetConfirmRequest>
    const validator = new Validator()
    const token = typeof body.token === 'string' ? body.token : ''
    if (token.trim() === '') {
      validator.errors.push({ field: 'token', message: '再設定のリンクが正しくありません。メールのリンクを、もう一度開いてください。' })
    }
    const newPassword = validator.password('newPassword', body.newPassword)
    if (validator.hasErrors()) {
      return problem(400, 'VALIDATION_ERROR', '入力内容に誤りがあります。', { errors: validator.errors })
    }

    // 知らない・期限切れ・使用済み・アカウントが削除済みは、どれも同じエラーにする
    const saved = db.passwordResetTokens.get(token)
    const user = saved && db.users.find((u) => u.id === saved.userId && u.deletedAt === null)
    if (!saved || !user || saved.usedAt !== null || saved.expiresAt <= Date.now()) {
      return problem(400, 'INVALID_RESET_TOKEN', 'このリンクは使えません（有効期限が切れたか、すでに使われています）。もう一度、再設定を依頼してください。')
    }

    // パスワードを変え、このユーザーの、まだ使っていないトークンを、すべて使えなくする
    user.password = newPassword
    for (const other of db.passwordResetTokens.values()) {
      if (other.userId === user.id && other.usedAt === null) {
        other.usedAt = Date.now()
      }
    }
    savePasswordResetTokens()
    // ログインのロックを解く
    db.loginFailures.delete(user.email.toLowerCase())
    // 偽の API のログインの Cookie は「user-番号」なので、ほかの端末のログインを止めることは、まねしていない。
    // 新しいパスワードも、ほかのデータと同じく、ページを読み込み直すと元（campfire2026）に戻る
    return new HttpResponse(null, { status: 204 })
  }),
]

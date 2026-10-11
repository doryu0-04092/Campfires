/**
 * 【役割】API が返した入力のエラーの一覧を、画面で使いやすい形に変える関数。
 * 【なぜ必要か】API は [{ field: 'email', message: '…' }] のような一覧で返すが、画面では
 * 「email のエラーの文は何か」をすぐ取り出したい。{ email: '…' } の形に変えておくと、入力欄ごとに表示しやすい。
 */

import type { FieldError } from '../types/api'

/**
 * 【何のため】エラーの一覧を、「項目の名前 → エラーの文」の形に変える。
 * Record<string, string> は「名前（文字）から値（文字）を引ける入れ物」の型。
 * 例：[{ field: 'email', message: '入力してください' }] → { email: '入力してください' }
 */
export function toFieldMessages(errors: FieldError[]): Record<string, string> {
  const messages: Record<string, string> = {}
  for (const error of errors) {
    // 同じ項目に複数のエラーがあるときは、最初のものだけを使う
    if (!(error.field in messages)) {
      messages[error.field] = error.message
    }
  }
  return messages
}

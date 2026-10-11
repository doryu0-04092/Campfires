/**
 * 【役割】偽の API で使う、入力のチェックの共通の処理。
 * 【なぜ必要か】文字数などのルールは、API 仕様書（docs/api/openapi.yaml）の schemas で決めている。
 * 多くの API で同じチェックをするので、1か所にまとめる。
 * 本物のバックエンドでは、Spring の入力チェックの仕組み（Bean Validation）がこの役割を担う。
 */

import type { FieldError } from '../../types/api'

/**
 * 【何のため】入力のチェックで見つかったエラーを集める入れ物。
 * チェックを1つずつ行い、最後に errors が空かどうかで、成功か失敗かを判断する。
 */
export class Validator {
  // 見つかったエラーの一覧
  readonly errors: FieldError[] = []

  /**
   * 【何のため】文字の項目を、決まった文字数の範囲でチェックする。
   * 文字でなければ（送られてこなかったなど）空の文字として扱う。
   * チェックに通ったら、前後の空白を取り除いた文字を返す。
   */
  text(field: string, value: unknown, label: string, min: number, max: number): string {
    // typeof value === 'string' は「value が文字かどうか」を確かめる書き方
    const text = typeof value === 'string' ? value.trim() : ''
    if (text.length < min) {
      this.errors.push({ field, message: min === 1 ? `${label}を入力してください。` : `${label}は${min}文字以上にしてください。` })
    } else if (text.length > max) {
      this.errors.push({ field, message: `${label}は${max}文字以内にしてください。` })
    }
    return text
  }

  /**
   * 【何のため】メールアドレスの形をチェックする。厳密ではなく、「@ の前後に文字がある」程度を確かめる。
   */
  email(field: string, value: unknown): string {
    const email = this.text(field, value, 'メールアドレス', 1, 254)
    // 正規表現：空白と @ 以外の文字 → @ → 空白と @ 以外の文字 → . → 空白と @ 以外の文字
    if (email !== '' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      this.errors.push({ field, message: 'メールアドレスの形が正しくありません。' })
    }
    return email
  }

  /**
   * 【何のため】パスワードのルールをチェックする（8〜64文字、半角の英字・数字・記号だけ）。
   * パスワードは、前後の空白も含めてそのまま使う（trim しない）。
   */
  password(field: string, value: unknown): string {
    const password = typeof value === 'string' ? value : ''
    if (password.length < 8 || password.length > 64) {
      this.errors.push({ field, message: 'パスワードは8〜64文字にしてください。' })
    } else if (!/^[\x21-\x7E]+$/.test(password)) {
      // \x21-\x7E は、半角の英字・数字・記号（空白を除く）の範囲
      this.errors.push({ field, message: 'パスワードには、半角の英字・数字・記号だけを使ってください。' })
    }
    return password
  }

  /**
   * 【何のため】エラーが1つでも見つかったかどうか。
   */
  hasErrors(): boolean {
    return this.errors.length > 0
  }
}

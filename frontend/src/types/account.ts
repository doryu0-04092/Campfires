/**
 * 【役割】アカウントとログインに関するデータの形（型）を決めるファイル。
 * 【なぜ必要か】画面と API の間でやり取りするデータの形を、API 仕様書（docs/api/openapi.yaml）と同じにするため。
 */

/**
 * 【何のため】ログインしている自分の情報（GET /api/me の返事）の形。
 * 本名とメールアドレスを含むので、本人にだけ返される（docs/api/README.md 1.9）。
 */
export type Me = {
  // ユーザーの番号
  id: number
  // 本名
  realName: string
  // ニックネーム（ほかのユーザーに表示される名前）
  nickname: string
  // メールアドレス
  email: string
  // 登録した日時（世界標準時）
  createdAt: string
}

/**
 * 【何のため】ログインのときに送るデータ（POST /api/auth/login）の形。
 */
export type LoginRequest = {
  email: string
  password: string
}

/**
 * 【何のため】アカウント登録のときに送るデータ（POST /api/auth/signup）の形。
 */
export type SignupRequest = {
  // 本名（1〜50文字）。ほかのユーザーには表示されない
  realName: string
  // ニックネーム（1〜30文字）
  nickname: string
  // メールアドレス
  email: string
  // パスワード（8〜64文字、半角の英字・数字・記号）
  password: string
  // プライバシーポリシーと利用規約に同意したか（true でなければ登録できない）
  agreedToPolicies: boolean
}

/**
 * 【何のため】ニックネームを変えるときに送るデータ（PATCH /api/me）の形。
 */
export type UpdateMeRequest = {
  nickname: string
}

/**
 * 【何のため】アカウントを削除するときに送るデータ（DELETE /api/me）の形。本人の確認のため、パスワードを送る。
 */
export type DeleteMeRequest = {
  password: string
}

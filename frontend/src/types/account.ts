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

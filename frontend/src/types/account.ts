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

/**
 * 【何のため】パスワードを変えるときに送るデータ（PUT /api/me/password）の形。本人の確認のため、今のパスワードも送る。
 */
export type ChangePasswordRequest = {
  // 今のパスワード
  currentPassword: string
  // 新しいパスワード（8〜64文字、半角の英字・数字・記号。アカウント登録と同じルール）
  newPassword: string
}

/**
 * 【何のため】パスワードの再設定を依頼するときに送るデータ（POST /api/auth/password-reset-requests）の形。
 */
export type PasswordResetRequest = {
  email: string
}

/**
 * 【何のため】パスワードの再設定（新しいパスワードを決める）のときに送るデータ（POST /api/auth/password-resets）の形。
 */
export type PasswordResetConfirmRequest = {
  // 再設定のリンク（メール）に入っていた、使い捨ての合言葉
  token: string
  // 新しいパスワード（アカウント登録と同じルール）
  newPassword: string
}

/**
 * 【何のため】今使える機能の一覧（GET /api/auth/features の返事）の形。
 */
export type AuthFeatures = {
  // パスワードの再設定（パスワードを忘れた場合）を使えるか。メールを送る仕組みがない本番では false
  passwordResetEnabled: boolean
}

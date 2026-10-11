/**
 * 【役割】ほかのユーザーを表すデータの形（型）を決めるファイル。
 * 【なぜ必要か】コミュニティのオーナー、メンバー、申込者など、ほかのユーザーを表すときは、
 * どこでも同じ形（ニックネームだけ）にしている（docs/api/README.md 1.9）。
 * docs/api/openapi.yaml の UserRef と同じ形。
 */

/**
 * 【何のため】ほかのユーザーを表すときの形。本名とメールアドレスは含まない。
 */
export type UserRef = {
  // ユーザーの番号
  userId: number
  // ニックネーム。アカウントを削除した人は null
  nickname: string | null
}

/**
 * 【何のため】画面に表示するユーザーの名前を返す。アカウントを削除した人は「退会したユーザー」（要件定義書 5.5）。
 */
export function displayName(user: UserRef): string {
  return user.nickname ?? '退会したユーザー'
}

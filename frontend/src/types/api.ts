/**
 * 【役割】すべての API で共通に使うデータの形（型）を決めるファイル。
 * 【なぜ必要か】エラーの返事や一覧のページ情報は、どの API でも同じ形にしている（docs/api/README.md 1.5、1.8）。
 * 形を1か所で決めておくと、API ごとに書き直さずに済むため。
 * docs/api/openapi.yaml の Problem、FieldError、PageInfo と同じ形にしている。
 */

/**
 * 【何のため】入力のチェックで引っかかった項目1つ分の形。
 */
export type FieldError = {
  // 引っかかった項目の名前（例："title"）
  field: string
  // 利用者に見せる、日本語の説明
  message: string
}

/**
 * 【何のため】エラーのときに API が返す形（RFC 9457 Problem Details）。
 */
export type Problem = {
  type: string
  // HTTP ステータスコードの短い説明（英語）
  title: string
  // HTTP ステータスコード（例：409）
  status: number
  // 利用者に見せてよい、日本語の説明
  detail: string
  // エラーの種類を表す決まった記号（例："EVENT_FULL"）。画面はこれを見て表示を変える
  code: string
  // 入力のチェックで引っかかった項目の一覧（400 のときだけ）。? は「ないこともある」という意味
  errors?: FieldError[]
}

/**
 * 【何のため】一覧を返す API で、ページの切り替えに使う情報の形。
 */
export type PageInfo = {
  // 全部の件数
  totalCount: number
  // 今のページ番号（1から始まる）
  page: number
  // 1ページの件数（30）
  pageSize: number
  // 全部で何ページあるか
  totalPages: number
}

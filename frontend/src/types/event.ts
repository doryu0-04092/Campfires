/**
 * 【役割】イベントのデータの形（型）を決めるファイル。
 * 【なぜ必要か】画面と API の間でやり取りするデータの形を1か所で決めておくと、
 * 名前の書き間違いや、足りない項目を TypeScript が見つけてくれるため。
 * ここで決めた形は、あとで作る API 仕様書の下書きにもなる。
 */

/**
 * 【何のため】イベント一覧に表示する、イベント1件分のデータの形。
 * 一覧で使う項目だけを持つ（説明などの長い項目は、イベント詳細で取得する）。
 */
export type EventSummary = {
  // イベントの番号
  id: number
  // どのコミュニティのイベントか（コミュニティの番号と名前）
  communityId: number
  communityName: string
  // イベントのタイトル
  title: string
  // 開始日時と終了日時。世界標準時の ISO 8601 形式の文字列（例："2026-10-10T10:00:00.000Z"）。
  // 日本時間への変換は、画面に表示するときに行う（非機能要件 6. 言語と時刻）
  startAt: string
  endAt: string
  // 定員
  capacity: number
  // 残りの枠数（0 なら満席）
  remainingSeats: number
}

/**
 * 【何のため】イベント一覧の API（GET /api/events）が返すデータの形。
 * 1ページ分のイベントと、ページの切り替えに必要な情報をまとめて返す。
 */
export type EventListResponse = {
  // このページに表示するイベント（最大 30件）
  items: EventSummary[]
  // 見つかったイベントの件数（検索しているときは、300件が上限）
  totalCount: number
  // 今のページ番号（1から始まる）
  page: number
  // 1ページの件数（30）
  pageSize: number
  // 全部で何ページあるか
  totalPages: number
  // 検索結果が 300件を超えたかどうか（超えたら true）
  limitExceeded: boolean
}

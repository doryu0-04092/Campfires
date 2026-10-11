/**
 * 【役割】イベントのデータの形（型）を決めるファイル。
 * 【なぜ必要か】画面と API の間でやり取りするデータの形を1か所で決めておくと、
 * 名前の書き間違いや、足りない項目を TypeScript が見つけてくれるため。
 * 形は API 仕様書（docs/api/openapi.yaml）の events と applications に合わせている。
 */

import type { PageInfo } from './api'
import type { CommunityRole } from './community'
import type { UserRef } from './user'

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
 * 【何のため】申込みの状態。applied：申込み済み、waitlisted：キャンセル待ち。
 */
export type ApplicationStatus = 'applied' | 'waitlisted'

/**
 * 【何のため】自分の申込みの状況。
 */
export type MyApplication = {
  status: ApplicationStatus
  // キャンセル待ちの何番目か（1から始まる）。申込み済みのときは null
  waitlistPosition: number | null
  appliedAt: string
}

/**
 * 【何のため】イベントの詳細（GET /api/communities/:communityId/events/:eventId）。
 * 一覧の情報に、説明、場所、締め切り、自分との関係などを足したもの。
 */
export type EventDetail = EventSummary & {
  description: string
  location: string
  // 申込み締め切り日時（キャンセルの期限も兼ねる。要件定義書 5.2）
  applicationDeadline: string
  // キャンセル待ちの人数
  waitlistCount: number
  // コミュニティをアーカイブした日時（していなければ null）
  communityArchivedAt: string | null
  // コミュニティでの自分の立場（メンバーでない、またはログインしていないときは null）
  myRole: CommunityRole | null
  // 自分の申込みの状況（申し込んでいない、またはログインしていないときは null）
  myApplication: MyApplication | null
  createdAt: string
}

/**
 * 【何のため】イベントを作成・編集するときに送るデータ。
 * 日時の前後関係のルール：今 < 開始日時、申込み締め切り日時 < 開始日時 < 終了日時
 */
export type EventInput = {
  title: string
  description: string
  startAt: string
  endAt: string
  applicationDeadline: string
  location: string
  capacity: number
}

/**
 * 【何のため】申込者一覧に表示する、1人分の情報。
 */
export type Application = {
  user: UserRef
  status: ApplicationStatus
  waitlistPosition: number | null
  appliedAt: string
}

export type ApplicationListResponse = PageInfo & {
  items: Application[]
}

/**
 * 【何のため】自分が申し込んだイベント1件分。一覧の情報に、自分の申込みの状況を足したもの。
 */
export type MyEvent = EventSummary & {
  myApplication: MyApplication
}

/**
 * 【何のため】自分が申し込んだイベントの一覧の返事（GET /api/me/events。全件。ページに分けない）。
 */
export type MyEventListResponse = {
  items: MyEvent[]
}

/**
 * 【何のため】コミュニティのイベントの一覧の返事（GET /api/communities/:communityId/events）。
 */
export type CommunityEventListResponse = PageInfo & {
  items: EventSummary[]
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

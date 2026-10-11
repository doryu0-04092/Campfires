/**
 * 【役割】お知らせに関するデータの形（型）を決めるファイル。
 * 【なぜ必要か】画面と API の間でやり取りするデータの形を、API 仕様書（docs/api/openapi.yaml）と同じにするため。
 */

import type { PageInfo } from './api'
import type { UserRef } from './user'

/**
 * 【何のため】お知らせの種類（要件定義書 5.10）。
 * - late_cancellation：締め切り後に、申込みがキャンセルされた（オーナーと運営スタッフあて）
 * - event_deleted：申し込んでいたイベントが削除された（申し込んでいた人あて）
 */
export type NotificationType = 'late_cancellation' | 'event_deleted'

/**
 * 【何のため】お知らせ1件分の情報。
 * 文章そのものではなく、種類と材料（イベントのタイトルなど）を受け取り、画面で文を組み立てる。
 */
export type Notification = {
  id: number
  type: NotificationType
  // 出来事が起きた時点のイベントのタイトル（イベントが削除されても残っている）
  eventTitle: string
  communityId: number
  // イベントの番号。イベントが削除されたお知らせでは null（移動先がない）
  eventId: number | null
  // 出来事を起こした人（締め切り後にキャンセルした人）。ないときは null
  actor: UserRef | null
  // 既読かどうか
  read: boolean
  createdAt: string
}

export type NotificationListResponse = PageInfo & {
  items: Notification[]
  // 未読のお知らせの件数（全部の中で）
  unreadCount: number
}

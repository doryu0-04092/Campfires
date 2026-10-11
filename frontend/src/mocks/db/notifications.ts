/**
 * 【役割】偽のデータベースの「お知らせ」の表と、最初に入れておくデータ。
 * 【なぜ必要か】お知らせの画面を試せるようにするため（要件定義書 5.10）。
 */

import type { MockEvent } from './events'

/**
 * 【何のため】偽のデータベースのお知らせ1件分の形。
 * 既読かどうかは read_at（既読にした日時）の1つの項目で管理する（null なら未読）。
 */
export type MockNotification = {
  id: number
  // お知らせを受け取るユーザーの番号
  userId: number
  // late_cancellation：締め切り後のキャンセル、event_deleted：イベントの削除
  type: 'late_cancellation' | 'event_deleted'
  // 出来事が起きた時点のイベントのタイトル（イベントが削除されても残す）
  eventTitle: string
  communityId: number
  // イベントが削除されたときは null
  eventId: number | null
  // 出来事を起こした人の番号（締め切り後にキャンセルした人）。ないときは null
  actorUserId: number | null
  // 既読にした日時。未読なら null
  readAt: string | null
  createdAt: string
}

const HOUR = 60 * 60 * 1000

/**
 * 【何のため】お知らせの表の最初のデータを作る。
 * たろう（1）あてに、締め切り後のキャンセルのお知らせを2件（未読1件、既読1件）入れておく。
 */
export function createNotifications(events: MockEvent[], now: number): MockNotification[] {
  // Java 勉強会（コミュニティ 1）のイベントを2つ選ぶ
  const [first, second] = events.filter((event) => event.communityId === 1)

  return [
    {
      id: 1,
      userId: 1,
      type: 'late_cancellation',
      eventTitle: first.title,
      communityId: first.communityId,
      eventId: first.id,
      actorUserId: 6,
      readAt: new Date(now - 20 * HOUR).toISOString(),
      createdAt: new Date(now - 24 * HOUR).toISOString(),
    },
    {
      id: 2,
      userId: 1,
      type: 'late_cancellation',
      eventTitle: second.title,
      communityId: second.communityId,
      eventId: second.id,
      actorUserId: 7,
      readAt: null,
      createdAt: new Date(now - 2 * HOUR).toISOString(),
    },
  ]
}

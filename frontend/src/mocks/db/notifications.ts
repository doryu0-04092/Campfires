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
  // late_cancellation：締め切り後のキャンセル、event_deleted：イベントの削除、
  // application_approved：参加の承認、application_rejected：参加の却下（要件定義書 5.12）
  type: 'late_cancellation' | 'event_deleted' | 'application_approved' | 'application_rejected'
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

// 古い既読のお知らせの数（スクロールで続きを読み込む動きを試すため、30件より多くする）
const OLD_READ_COUNT = 45

/**
 * 【何のため】お知らせの表の最初のデータを作る。
 * たろう（1）あてに、締め切り後のキャンセルのお知らせを入れておく。
 * - 新しいもの 2件（未読1件、既読1件）
 * - 古い既読のもの 45件（既読のお知らせが残ることと、スクロールで続きを読み込む動きを試すため）
 */
export function createNotifications(events: MockEvent[], now: number): MockNotification[] {
  // Java 勉強会（コミュニティ 1）のイベントを選ぶ
  const javaEvents = events.filter((event) => event.communityId === 1)
  const [first, second] = javaEvents

  // 古い既読のお知らせ。番号は 3 から。1日ずつ古くなるようにする
  const oldReadNotifications: MockNotification[] = Array.from({ length: OLD_READ_COUNT }, (_, i) => {
    const event = javaEvents[i % javaEvents.length]
    return {
      id: 3 + i,
      userId: 1,
      type: 'late_cancellation',
      eventTitle: event.title,
      communityId: event.communityId,
      eventId: event.id,
      // 参加者03〜参加者12 の誰かがキャンセルしたことにする
      actorUserId: 7 + (i % 10),
      readAt: new Date(now - (2 + i) * 24 * HOUR).toISOString(),
      createdAt: new Date(now - (2 + i) * 24 * HOUR - HOUR).toISOString(),
    }
  })

  return [
    ...oldReadNotifications,
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

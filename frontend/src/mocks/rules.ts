/**
 * 【役割】偽の API で、複数の API から使う「業務ルール」の処理をまとめたファイル。
 * 【なぜ必要か】同じルールを API ごとに書くと、直し忘れが起きるため、1か所にまとめる。
 * 本物のバックエンドでは、Service（業務処理）のクラスがこの役割を担う。
 */

import { db, nextId } from './db'
import type { MockApplication, MockEvent } from './db/events'
import type { MockNotification } from './db/notifications'

/**
 * 【何のため】イベントの申込み済み（キャンセル待ちを除く）の人数を数える。
 */
export function countApplied(eventId: number): number {
  return db.applications.filter((a) => a.eventId === eventId && a.status === 'applied').length
}

/**
 * 【何のため】イベントの承認待ちの人数を数える（要件定義書 5.12）。
 */
export function countPending(eventId: number): number {
  return db.applications.filter((a) => a.eventId === eventId && a.status === 'pending').length
}

/**
 * 【何のため】席の順番を比べる関数（席の順番を取った日時の古い順、同じなら番号の小さい順）。
 * 席の順番は queuedAt で決める。自動なら申し込んだ日時、手動なら承認された日時（要件定義書 5.12）。
 */
function byQueue(a: MockApplication, b: MockApplication): number {
  return (a.queuedAt ?? '').localeCompare(b.queuedAt ?? '') || a.id - b.id
}

/**
 * 【何のため】イベントのキャンセル待ちの人を、席の順番に並べて返す（要件定義書 5.3）。
 */
export function waitlistOf(eventId: number): MockApplication[] {
  return db.applications.filter((a) => a.eventId === eventId && a.status === 'waitlisted').sort(byQueue)
}

/**
 * 【何のため】イベントの申込み済みの人を、席の順番に並べて返す。
 */
export function appliedOf(eventId: number): MockApplication[] {
  return db.applications.filter((a) => a.eventId === eventId && a.status === 'applied').sort(byQueue)
}

/**
 * 【何のため】イベントの承認待ちの人を、申し込んだ順に並べて返す。
 */
export function pendingOf(eventId: number): MockApplication[] {
  return db.applications
    .filter((a) => a.eventId === eventId && a.status === 'pending')
    .sort((a, b) => a.appliedAt.localeCompare(b.appliedAt) || a.id - b.id)
}

/**
 * 【何のため】締め切り日時を過ぎているかどうか。
 */
export function isDeadlinePassed(event: MockEvent, now = new Date()): boolean {
  return new Date(event.applicationDeadline) <= now
}

/**
 * 【何のため】イベントが開始しているかどうか（承認・却下は、開始日時まで）。
 */
export function isStarted(event: MockEvent, now = new Date()): boolean {
  return new Date(event.startAt) <= now
}

/**
 * 【何のため】イベントが終了しているかどうか。
 */
export function isEnded(event: MockEvent, now = new Date()): boolean {
  return new Date(event.endAt) <= now
}

/**
 * 【何のため】ユーザーが、イベントのコミュニティのメンバーかどうか。
 */
export function isMemberOf(event: MockEvent, userId: number): boolean {
  return db.memberships.some((m) => m.communityId === event.communityId && m.userId === userId)
}

/**
 * 【何のため】空いている枠の数だけ、キャンセル待ちの先頭から申込み済みに繰り上げる（要件定義書 5.3、F-16）。
 * 締め切り後は繰り上げない。
 */
export function promoteWaitlist(event: MockEvent): void {
  if (isDeadlinePassed(event)) {
    return
  }
  // 空いている枠の数 = 定員 - 申込み済みの人数
  let freeSeats = event.capacity - countApplied(event.id)
  for (const application of waitlistOf(event.id)) {
    if (freeSeats <= 0) {
      break
    }
    application.status = 'applied'
    freeSeats--
  }
}

/**
 * 【何のため】お知らせを1件作る（要件定義書 5.10）。
 */
function notify(userId: number, type: MockNotification['type'], event: MockEvent, actorUserId: number | null): void {
  db.notifications.push({
    id: nextId(db.notifications),
    userId,
    type,
    // 出来事が起きた時点のタイトルを残す（イベントが削除されても、何のイベントか分かるように）
    eventTitle: event.title,
    communityId: event.communityId,
    // イベントが削除されたときは、移動先がないので null
    eventId: type === 'event_deleted' ? null : event.id,
    actorUserId: type === 'late_cancellation' ? actorUserId : null,
    readAt: null,
    createdAt: new Date().toISOString(),
  })
}

/**
 * 【何のため】申込みを1件取り消し、要件定義書のルールどおりに後の処理をする。
 * - 承認待ち：席を使っていないので、取り消すだけ（繰り上げもお知らせもしない。5.12）
 * - 締め切り前：申込み済みの人が取り消したときは、キャンセル待ちの先頭の人を繰り上げる（5.3）
 * - 締め切り後：繰り上げない。オーナーと運営スタッフにお知らせを送る（5.2、5.10）
 * @param application 取り消す申込み（却下された申込みは、取り消せないので渡さない）
 * @param operatorUserId 取り消しの操作をした人（本人、または除名したオーナー）。この人にはお知らせを送らない
 */
export function cancelApplication(application: MockApplication, operatorUserId: number): void {
  const event = db.events.find((e) => e.id === application.eventId)!
  db.applications = db.applications.filter((a) => a !== application)

  if (application.status === 'pending') {
    return
  }
  if (isDeadlinePassed(event)) {
    // 締め切り後のキャンセル：そのコミュニティのオーナーと運営スタッフに知らせる（操作した本人を除く）
    const managers = db.memberships.filter(
      (m) => m.communityId === event.communityId && (m.role === 'owner' || m.role === 'staff') && m.userId !== operatorUserId,
    )
    for (const manager of managers) {
      notify(manager.userId, 'late_cancellation', event, application.userId)
    }
  } else if (application.status === 'applied') {
    // 締め切り前に、申込み済みの人が取り消した：空いた枠に、キャンセル待ちの人を繰り上げる
    promoteWaitlist(event)
  }
}

/**
 * 【何のため】承認待ちの申込みを承認する（要件定義書 5.12）。
 * 承認したときに席を取る。空きがあれば申込み済み、満席ならキャンセル待ち（順番は承認された順）。
 * 申し込んだ人に、承認されたことを知らせる。
 */
export function approveApplication(event: MockEvent, application: MockApplication): void {
  application.status = countApplied(event.id) < event.capacity ? 'applied' : 'waitlisted'
  application.queuedAt = new Date().toISOString()
  notify(application.userId, 'application_approved', event, null)
}

/**
 * 【何のため】承認待ちの申込みを却下する。申し込んだ人に、却下されたことを知らせる。
 */
export function rejectApplication(event: MockEvent, application: MockApplication): void {
  application.status = 'rejected'
  notify(application.userId, 'application_rejected', event, null)
}

/**
 * 【何のため】承認待ちの申込みを、申し込んだ順にまとめて承認する（承認の方法を「手動」から「自動」に変えたとき）。
 * @param member true ならメンバーの申込み、false ならメンバー以外の申込みを承認する
 */
export function approveAllPending(event: MockEvent, member: boolean): void {
  for (const application of pendingOf(event.id)) {
    if (isMemberOf(event, application.userId) === member) {
      approveApplication(event, application)
    }
  }
}

/**
 * 【何のため】ユーザーの、開催前のイベントへの申込み（承認待ち、キャンセル待ちを含む）を、すべて取り消す。
 * コミュニティ退会（要件定義書 5.4）、除名（5.4.1）、アカウント削除（5.5）で使う。
 * 1件ずつ cancelApplication を通すので、繰り上げとお知らせも、ルールどおりに行われる。
 * 却下された申込みは、記録として残す。
 * @param userId 申込みを取り消すユーザー
 * @param operatorUserId 取り消しの操作をした人（退会・アカウント削除なら本人、除名ならオーナー）
 * @param communityId 指定したときは、そのコミュニティのイベントだけを対象にする
 */
export function cancelUpcomingApplications(userId: number, operatorUserId: number, communityId?: number): void {
  const now = new Date()
  const targets = db.applications.filter((a) => {
    if (a.userId !== userId || a.status === 'rejected') {
      return false
    }
    const event = db.events.find((e) => e.id === a.eventId)!
    return !isEnded(event, now) && (communityId === undefined || event.communityId === communityId)
  })
  for (const application of targets) {
    cancelApplication(application, operatorUserId)
  }
}

/**
 * 【何のため】イベントを削除する。申込みも一緒に削除し、申し込んでいた人にお知らせを送る（要件定義書 5.2、5.10）。
 * 承認待ちの人にも送る。却下された人には送らない。
 * @param event 削除するイベント
 * @param operatorUserId 削除の操作をした人。この人にはお知らせを送らない
 */
export function deleteEvent(event: MockEvent, operatorUserId: number): void {
  const applicants = db.applications.filter((a) => a.eventId === event.id && a.status !== 'rejected')
  for (const application of applicants) {
    if (application.userId !== operatorUserId) {
      notify(application.userId, 'event_deleted', event, null)
    }
  }
  db.applications = db.applications.filter((a) => a.eventId !== event.id)
  db.events = db.events.filter((e) => e !== event)
}

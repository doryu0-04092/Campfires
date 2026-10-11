/**
 * 【役割】偽の API で、複数の API から使う「業務ルール」の処理をまとめたファイル。
 * 【なぜ必要か】同じルールを API ごとに書くと、直し忘れが起きるため、1か所にまとめる。
 * 本物のバックエンドでは、Service（業務処理）のクラスがこの役割を担う。
 */

import { db } from './db'

/**
 * 【何のため】ユーザーの、開催前のイベントへの申込み（キャンセル待ちを含む）を、すべて取り消す。
 * コミュニティ退会（要件定義書 5.4）、除名（5.4.1）、アカウント削除（5.5）で使う。
 * @param userId 申込みを取り消すユーザー
 * @param communityId 指定したときは、そのコミュニティのイベントだけを対象にする。省略したときは、すべてのコミュニティ
 *
 * キャンセル待ちの繰り上げ（5.3）は、イベントの画面を作るとき（Issue #23）に、ここに足す。
 */
export function cancelUpcomingApplications(userId: number, communityId?: number): void {
  const now = new Date()
  // 対象のイベント：開催前（終了日時が今より後）で、指定があればそのコミュニティのもの
  const targetEventIds = db.events
    .filter((e) => new Date(e.endAt) > now && (communityId === undefined || e.communityId === communityId))
    .map((e) => e.id)

  // 対象のイベントへの、そのユーザーの申込みを取り除く
  db.applications = db.applications.filter((a) => a.userId !== userId || !targetEventIds.includes(a.eventId))
}

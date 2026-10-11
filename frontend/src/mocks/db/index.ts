/**
 * 【役割】偽のデータベース全体。各表をまとめて、偽の API から使えるようにする。
 * 【なぜ必要か】偽の API が、本物のバックエンドと同じように「データベースを読み書きして返事をする」形で
 * 作れるようにするため。データはブラウザのメモリの中にだけあり、ページを読み込み直すと最初の状態に戻る。
 * 開発中だけ使い、本番用のファイルには含まれない。
 */

import { createCommunities, createMemberships, type MockCommunity, type MockMembership } from './communities'
import { createApplications, createEvents, type MockApplication, type MockEvent } from './events'
import { createNotifications, type MockNotification } from './notifications'
import { createUsers, type MockUser } from './users'

/**
 * 【何のため】ログインの失敗の記録（ログインの試行制限のため。非機能要件 3章）。
 */
export type LoginFailure = {
  // 続けて失敗した回数
  count: number
  // ロックが解ける日時（ミリ秒）。ロックされていなければ null
  lockedUntil: number | null
}

/**
 * 【何のため】偽のデータベース全体の形。
 */
export type MockDb = {
  users: MockUser[]
  communities: MockCommunity[]
  memberships: MockMembership[]
  events: MockEvent[]
  applications: MockApplication[]
  notifications: MockNotification[]
  // メールアドレスごとの、ログインの失敗の記録。Map は「名前（キー）と値の組」をしまう入れ物
  loginFailures: Map<string, LoginFailure>
}

/**
 * 【何のため】偽のデータベースを、最初の状態で作る。
 * 表どうしのつながり（イベントはコミュニティに、申込みはイベントに属する）があるので、順番に作る。
 */
function createDb(): MockDb {
  const now = Date.now()
  const users = createUsers(now)
  const communities = createCommunities(now)
  const memberships = createMemberships(communities, now)
  const events = createEvents(communities, now)
  const applications = createApplications(events)
  const notifications = createNotifications(events, now)

  return { users, communities, memberships, events, applications, notifications, loginFailures: new Map() }
}

// 偽のデータベース。偽の API（src/mocks/handlers）から読み込んで使う
export const db: MockDb = createDb()

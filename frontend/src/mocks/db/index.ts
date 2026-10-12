/**
 * 【役割】偽のデータベース全体。各表をまとめて、偽の API から使えるようにする。
 * 【なぜ必要か】偽の API が、本物のバックエンドと同じように「データベースを読み書きして返事をする」形で
 * 作れるようにするため。データはブラウザのメモリの中にだけあり、ページを読み込み直すと最初の状態に戻る。
 * 開発中だけ使い、本番用のファイルには含まれない。
 */

import {
  createCommunities,
  createJoinRequests,
  createMemberships,
  type MockCommunity,
  type MockJoinRequest,
  type MockMembership,
} from './communities'
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
 * 【何のため】パスワードの再設定のトークン（要件定義書 5.13）。
 * 本物は、トークンを SHA-256 で変換した値だけを保存するが、偽物は、分かりやすさのため、トークンをそのままキーにする。
 */
export type PasswordResetToken = {
  userId: number
  // 有効期限（ミリ秒）
  expiresAt: number
  // 使った・無効にした日時（ミリ秒）。まだ使えるなら null
  usedAt: number | null
}

/**
 * 【何のため】偽のデータベース全体の形。
 */
export type MockDb = {
  users: MockUser[]
  communities: MockCommunity[]
  memberships: MockMembership[]
  joinRequests: MockJoinRequest[]
  events: MockEvent[]
  applications: MockApplication[]
  notifications: MockNotification[]
  // メールアドレスごとの、ログインの失敗の記録。Map は「名前（キー）と値の組」をしまう入れ物
  loginFailures: Map<string, LoginFailure>
  // トークン → パスワードの再設定のトークンの情報
  passwordResetTokens: Map<string, PasswordResetToken>
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
  const joinRequests = createJoinRequests(now)
  const events = createEvents(communities, now)
  const applications = createApplications(events)
  const notifications = createNotifications(events, now)

  return {
    users,
    communities,
    memberships,
    joinRequests,
    events,
    applications,
    notifications,
    loginFailures: new Map(),
    passwordResetTokens: loadPasswordResetTokens(),
  }
}

// パスワードの再設定のトークンを、ブラウザの sessionStorage（タブを閉じるまで残る入れ物）に置くときの名前
const RESET_TOKENS_KEY = 'mockPasswordResetTokens'

/**
 * 【何のため】sessionStorage から、パスワードの再設定のトークンを読み込む。
 * 偽のデータベースは、ページを読み込み直すと最初の状態に戻る。再設定のリンクを開くと、ページ全体を読み込み直すので、
 * トークンだけは sessionStorage に置いて、リンクを開いた後も使えるようにする（偽の API だけの工夫）。
 * 読めないとき（ブラウザの設定で使えないなど）は、空の入れ物にする。
 */
function loadPasswordResetTokens(): Map<string, PasswordResetToken> {
  try {
    const saved = sessionStorage.getItem(RESET_TOKENS_KEY)
    // JSON.parse で、保存した文字を [トークン, 情報] の組の一覧に戻し、Map にする
    return new Map(saved ? (JSON.parse(saved) as [string, PasswordResetToken][]) : [])
  } catch {
    return new Map()
  }
}

/**
 * 【何のため】パスワードの再設定のトークンを、sessionStorage に保存する（トークンを作った・使ったときに呼ぶ）。
 */
export function savePasswordResetTokens(): void {
  try {
    // [...map] は、Map を [キー, 値] の組の一覧に変える書き方
    sessionStorage.setItem(RESET_TOKENS_KEY, JSON.stringify([...db.passwordResetTokens]))
  } catch {
    // 保存できなくても、偽の API は動き続ける（ページを読み込み直すと、トークンが消えるだけ）
  }
}

// 偽のデータベース。偽の API（src/mocks/handlers）から読み込んで使う
export const db: MockDb = createDb()

/**
 * 【何のため】表に新しい行を足すときの番号を決める（今いちばん大きい番号の次）。
 * 本物のデータベースでは、番号はデータベースが自動で付ける（PostgreSQL の IDENTITY）。
 */
export function nextId(rows: { id: number }[]): number {
  return rows.length === 0 ? 1 : Math.max(...rows.map((row) => row.id)) + 1
}

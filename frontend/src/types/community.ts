/**
 * 【役割】コミュニティとメンバーに関するデータの形（型）を決めるファイル。
 * 【なぜ必要か】画面と API の間でやり取りするデータの形を、API 仕様書（docs/api/openapi.yaml）と同じにするため。
 */

import type { PageInfo } from './api'
import type { UserRef } from './user'

/**
 * 【何のため】コミュニティの中での立場。owner：オーナー、staff：運営スタッフ、member：メンバー。
 */
export type CommunityRole = 'owner' | 'staff' | 'member'

// 立場の日本語の名前。画面に表示するときに使う
export const ROLE_LABELS: Record<CommunityRole, string> = {
  owner: 'オーナー',
  staff: '運営スタッフ',
  member: 'メンバー',
}

/**
 * 【何のため】参加申請の状態。pending：申請中、approved：承認済み、rejected：却下。
 */
export type JoinRequestStatus = 'pending' | 'approved' | 'rejected'

/**
 * 【何のため】コミュニティの一覧に表示する、1件分の情報。
 */
export type CommunitySummary = {
  id: number
  name: string
  description: string
  // メンバーの人数（オーナー、運営スタッフを含む）
  memberCount: number
  createdAt: string
}

/**
 * 【何のため】コミュニティの一覧の返事。& は「2つの形を合わせた形」という意味。
 */
export type CommunityListResponse = PageInfo & {
  items: CommunitySummary[]
  // 検索結果が 300件を超えたかどうか
  limitExceeded: boolean
}

/**
 * 【何のため】自分がメンバーとして参加しているコミュニティの一覧の返事（全件。ページに分けない）。
 */
export type MyCommunityListResponse = {
  items: CommunitySummary[]
}

/**
 * 【何のため】コミュニティの詳細。一覧の情報に、オーナーと、自分との関係を足したもの。
 */
export type CommunityDetail = CommunitySummary & {
  owner: UserRef
  // アーカイブした日時。アーカイブしていなければ null（要件定義書 5.9）
  archivedAt: string | null
  // 自分の立場。メンバーでない、またはログインしていないときは null
  myRole: CommunityRole | null
  // 自分の参加申請の状態。申請していない、ログインしていない、またはすでにメンバーのときは null
  myJoinRequestStatus: JoinRequestStatus | null
}

/**
 * 【何のため】コミュニティを作成・編集するときに送るデータ。
 */
export type CommunityInput = {
  // 名前（1〜50文字）
  name: string
  // 説明（2000文字まで）
  description: string
}

/**
 * 【何のため】管理しているコミュニティの一覧に表示する、1件分の情報。
 */
export type ManagedCommunity = {
  id: number
  name: string
  memberCount: number
  archivedAt: string | null
  // 自分の立場（owner か staff）
  myRole: CommunityRole
  // 申請中の参加申請の件数
  pendingJoinRequestCount: number
}

export type ManagedCommunityListResponse = PageInfo & {
  items: ManagedCommunity[]
}

/**
 * 【何のため】参加申請1件分の情報。
 */
export type JoinRequest = {
  id: number
  user: UserRef
  status: JoinRequestStatus
  requestedAt: string
}

export type JoinRequestListResponse = PageInfo & {
  items: JoinRequest[]
}

/**
 * 【何のため】メンバー1人分の情報。
 */
export type Member = {
  user: UserRef
  role: CommunityRole
  joinedAt: string
}

export type MemberListResponse = PageInfo & {
  items: Member[]
}

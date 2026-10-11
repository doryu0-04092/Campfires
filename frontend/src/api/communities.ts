/**
 * 【役割】コミュニティとメンバーに関する API を呼び出す関数をまとめたファイル。
 * 【なぜ必要か】API の URL や呼び出し方を画面のコードに直接書かず、ここに集めておくため。
 * URL と返事の形は、API 仕様書（docs/api/openapi.yaml）の communities と members に合わせている。
 */

import type {
  CommunityDetail,
  CommunityInput,
  CommunityListResponse,
  JoinRequest,
  JoinRequestListResponse,
  ManagedCommunityListResponse,
  MyCommunityListResponse,
  Member,
  MemberListResponse,
} from '../types/community'
import { apiRequest } from './client'

/**
 * 【何のため】ページ番号を URL の「?page=2」の形にする。1ページ目なら付けない。
 */
function pageQuery(page: number): string {
  return page > 1 ? `?page=${page}` : ''
}

// ---------- コミュニティ ----------

/**
 * 【何のため】コミュニティの一覧・検索を取得する（GET /api/communities）。
 * query が空なら、すべてのコミュニティ。空でなければ、名前と説明で検索する。
 */
export function fetchCommunities(query: string, page: number, signal?: AbortSignal) {
  const params = new URLSearchParams()
  if (query !== '') {
    params.set('q', query)
  }
  if (page > 1) {
    params.set('page', String(page))
  }
  return apiRequest<CommunityListResponse>(`/api/communities?${params.toString()}`, { signal })
}

/** 【何のため】自分がメンバーとして参加しているコミュニティの一覧を取得する（GET /api/me/communities）。 */
export function fetchMyCommunities(signal?: AbortSignal) {
  return apiRequest<MyCommunityListResponse>('/api/me/communities', { signal })
}

/** 【何のため】管理しているコミュニティの一覧を取得する（GET /api/me/managed-communities）。 */
export function fetchManagedCommunities(page: number, signal?: AbortSignal) {
  return apiRequest<ManagedCommunityListResponse>(`/api/me/managed-communities${pageQuery(page)}`, { signal })
}

/** 【何のため】コミュニティの詳細を取得する（GET /api/communities/:communityId）。 */
export function fetchCommunity(communityId: number, signal?: AbortSignal) {
  return apiRequest<CommunityDetail>(`/api/communities/${communityId}`, { signal })
}

/** 【何のため】コミュニティを作成する（POST /api/communities）。 */
export function createCommunity(input: CommunityInput) {
  return apiRequest<CommunityDetail>('/api/communities', { method: 'POST', body: input })
}

/** 【何のため】コミュニティを編集する（PATCH /api/communities/:communityId）。 */
export function updateCommunity(communityId: number, input: CommunityInput) {
  return apiRequest<CommunityDetail>(`/api/communities/${communityId}`, { method: 'PATCH', body: input })
}

/** 【何のため】コミュニティをアーカイブする（PUT /api/communities/:communityId/archive）。 */
export function archiveCommunity(communityId: number) {
  return apiRequest<CommunityDetail>(`/api/communities/${communityId}/archive`, { method: 'PUT' })
}

/** 【何のため】アーカイブを元に戻す（DELETE /api/communities/:communityId/archive）。 */
export function unarchiveCommunity(communityId: number) {
  return apiRequest<CommunityDetail>(`/api/communities/${communityId}/archive`, { method: 'DELETE' })
}

// ---------- メンバー ----------

/** 【何のため】参加を申請する（POST /api/communities/:communityId/join-requests）。 */
export function requestToJoin(communityId: number) {
  return apiRequest<JoinRequest>(`/api/communities/${communityId}/join-requests`, { method: 'POST' })
}

/** 【何のため】申請中の参加申請の一覧を取得する（GET /api/communities/:communityId/join-requests）。 */
export function fetchJoinRequests(communityId: number, signal?: AbortSignal) {
  return apiRequest<JoinRequestListResponse>(`/api/communities/${communityId}/join-requests`, { signal })
}

/** 【何のため】参加申請を承認・却下する（PATCH /api/communities/:communityId/join-requests/:joinRequestId）。 */
export function decideJoinRequest(communityId: number, joinRequestId: number, status: 'approved' | 'rejected') {
  return apiRequest<JoinRequest>(`/api/communities/${communityId}/join-requests/${joinRequestId}`, {
    method: 'PATCH',
    body: { status },
  })
}

/**
 * 【何のため】メンバーの一覧を取得する（GET /api/communities/:communityId/members）。
 * @param group managers ならオーナーと運営スタッフだけ、members なら一般のメンバーだけ
 */
export function fetchMembers(communityId: number, group: 'managers' | 'members', page: number, signal?: AbortSignal) {
  const params = new URLSearchParams({ group })
  if (page > 1) {
    params.set('page', String(page))
  }
  return apiRequest<MemberListResponse>(`/api/communities/${communityId}/members?${params.toString()}`, { signal })
}

/** 【何のため】運営スタッフに任命・解除する（PATCH /api/communities/:communityId/members/:userId）。 */
export function updateMemberRole(communityId: number, userId: number, role: 'staff' | 'member') {
  return apiRequest<Member>(`/api/communities/${communityId}/members/${userId}`, { method: 'PATCH', body: { role } })
}

/** 【何のため】メンバーを除名する（DELETE /api/communities/:communityId/members/:userId）。 */
export function removeMember(communityId: number, userId: number) {
  return apiRequest<void>(`/api/communities/${communityId}/members/${userId}`, { method: 'DELETE' })
}

/** 【何のため】自分がコミュニティを退会する（DELETE /api/communities/:communityId/membership）。 */
export function leaveCommunity(communityId: number) {
  return apiRequest<void>(`/api/communities/${communityId}/membership`, { method: 'DELETE' })
}

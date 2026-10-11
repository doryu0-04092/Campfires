/**
 * 【役割】コミュニティとメンバーの偽の API（docs/api/openapi.yaml の communities、members、account の managed-communities）。
 * 【なぜ必要か】バックエンドができるまでの間、コミュニティとメンバー管理の画面の動きを確かめるため。
 * 権限のチェック（オーナーだけ、オーナーと運営スタッフだけ）は、要件定義書 3章の表のとおりにしている。
 */

import { delay, http, HttpResponse } from 'msw'
import type {
  CommunityDetail,
  CommunityInput,
  CommunitySummary,
  JoinRequest,
  ManagedCommunity,
  Member,
} from '../../types/community'
import { db, nextId } from '../db'
import type { MockCommunity, MockJoinRequest, MockMembership, MockRole } from '../db/communities'
import { cancelUpcomingApplications } from '../rules'
import { searchCommunities } from '../search'
import {
  checkCsrf,
  findLoggedInUser,
  findRole,
  paginate,
  parsePage,
  problem,
  requireLogin,
  toUserRef,
} from './common'
import { Validator } from './validation'

// 立場の並び順（メンバーの一覧で使う）。オーナー → 運営スタッフ → メンバー
const ROLE_ORDER: Record<MockRole, number> = { owner: 0, staff: 1, member: 2 }

/**
 * 【何のため】URL の :communityId からコミュニティを探す。見つからなければ 404 の返事を返す。
 */
function findCommunity(params: Record<string, string | readonly string[] | undefined>): MockCommunity | Response {
  const id = Number(params.communityId)
  return db.communities.find((c) => c.id === id) ?? problem(404, 'NOT_FOUND', 'コミュニティが見つかりません。')
}

/**
 * 【何のため】コミュニティのメンバーの人数を数える。
 */
function countMembers(communityId: number): number {
  return db.memberships.filter((m) => m.communityId === communityId).length
}

/**
 * 【何のため】偽のデータベースのコミュニティを、一覧に返す形（CommunitySummary）に変える。
 */
function toSummary(community: MockCommunity): CommunitySummary {
  return {
    id: community.id,
    name: community.name,
    description: community.description,
    memberCount: countMembers(community.id),
    createdAt: community.createdAt,
  }
}

/**
 * 【何のため】コミュニティの詳細（CommunityDetail）を作る。見ている人（userId）との関係も入れる。
 */
function toDetail(community: MockCommunity, userId: number | undefined): CommunityDetail {
  const myRole = findRole(community.id, userId)
  // メンバーでなければ、自分の最新の参加申請の状態を返す（メンバーなら null）
  let myJoinRequestStatus: CommunityDetail['myJoinRequestStatus'] = null
  if (userId !== undefined && myRole === null) {
    // 自分の申請のうち、いちばん新しいもの。at(-1) は「一覧の最後の1つ」
    const latest = db.joinRequests.filter((r) => r.communityId === community.id && r.userId === userId).at(-1)
    // 承認済みなのにメンバーでない（退会した・除名された）場合は、申請していない扱いにする
    myJoinRequestStatus = latest && latest.status !== 'approved' ? latest.status : null
  }
  return {
    ...toSummary(community),
    owner: toUserRef(community.ownerId),
    archivedAt: community.archivedAt,
    myRole,
    myJoinRequestStatus,
  }
}

/**
 * 【何のため】参加申請を、画面に返す形（JoinRequest）に変える。
 */
function toJoinRequest(request: MockJoinRequest): JoinRequest {
  return { id: request.id, user: toUserRef(request.userId), status: request.status, requestedAt: request.requestedAt }
}

/**
 * 【何のため】メンバーの行を、画面に返す形（Member）に変える。
 */
function toMember(membership: MockMembership): Member {
  return { user: toUserRef(membership.userId), role: membership.role, joinedAt: membership.joinedAt }
}

/**
 * 【何のため】コミュニティの作成・編集で送られてきたデータをチェックする（docs/api/openapi.yaml の CommunityInput）。
 */
function validateCommunityInput(body: Partial<CommunityInput>): CommunityInput | Response {
  const validator = new Validator()
  const name = validator.text('name', body.name, 'コミュニティの名前', 1, 50)
  const description = validator.text('description', body.description, '説明', 0, 2000)
  if (validator.hasErrors()) {
    return problem(400, 'VALIDATION_ERROR', '入力内容に誤りがあります。', { errors: validator.errors })
  }
  return { name, description }
}

// 権限がないときの返事（403）
const forbidden = () => problem(403, 'FORBIDDEN', 'この操作をする権限がありません。')

export const communityHandlers = [
  /**
   * 管理しているコミュニティの一覧（GET /api/me/managed-communities）。アーカイブ済みも含める。
   */
  http.get('/api/me/managed-communities', async ({ request, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    // 自分がオーナーか運営スタッフのコミュニティの番号と立場
    const managed = db.memberships.filter((m) => m.userId === user.id && (m.role === 'owner' || m.role === 'staff'))
    const items: ManagedCommunity[] = managed
      .map((m) => {
        const community = db.communities.find((c) => c.id === m.communityId)!
        return {
          id: community.id,
          name: community.name,
          memberCount: countMembers(community.id),
          archivedAt: community.archivedAt,
          myRole: m.role,
          pendingJoinRequestCount: db.joinRequests.filter((r) => r.communityId === community.id && r.status === 'pending').length,
          // 並び替えのためだけに使う（返事には入れない）
          createdAt: community.createdAt,
        }
      })
      // 作成日時の新しい順
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      // createdAt を取り除く。{ createdAt, ...rest } は「createdAt 以外を rest にまとめる」書き方。_ は使わない値の印
      .map(({ createdAt: _createdAt, ...rest }) => rest)
    return HttpResponse.json(paginate(items, parsePage(request)))
  }),

  /**
   * コミュニティの一覧（GET /api/communities）。アーカイブ済みは含めない。作成日時の新しい順。
   */
  http.get('/api/communities', async ({ request }) => {
    await delay(300)
    // q があれば検索する。検索のルールは、イベントと同じ（src/mocks/search.ts）
    const query = new URL(request.url).searchParams.get('q') ?? ''
    return HttpResponse.json(searchCommunities(db, query, parsePage(request)))
  }),

  /**
   * 自分がメンバーとして参加しているコミュニティの一覧（GET /api/me/communities）。全件返す（要件定義書 5.11）。
   * オーナー・運営スタッフのコミュニティと、アーカイブ済みのコミュニティは含めない。
   */
  http.get('/api/me/communities', async ({ cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const items = db.memberships
      .filter((m) => m.userId === user.id && m.role === 'member')
      .map((m) => db.communities.find((c) => c.id === m.communityId)!)
      .filter((c) => c.archivedAt === null)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(toSummary)
    return HttpResponse.json({ items })
  }),

  /**
   * コミュニティを作成する（POST /api/communities）。作った人がオーナーになる。
   */
  http.post('/api/communities', async ({ request, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const input = validateCommunityInput((await request.json()) as Partial<CommunityInput>)
    if (input instanceof Response) {
      return input
    }

    const now = new Date().toISOString()
    const community: MockCommunity = {
      id: nextId(db.communities),
      ...input,
      ownerId: user.id,
      archivedAt: null,
      createdAt: now,
    }
    db.communities.push(community)
    // 作った人を、オーナーとしてメンバーに入れる
    db.memberships.push({ communityId: community.id, userId: user.id, role: 'owner', joinedAt: now })

    return HttpResponse.json(toDetail(community, user.id), {
      status: 201,
      headers: { Location: `/api/communities/${community.id}` },
    })
  }),

  /**
   * コミュニティの詳細（GET /api/communities/:communityId）。ログインしていなくても見られる。
   */
  http.get('/api/communities/:communityId', async ({ params, cookies }) => {
    await delay(300)
    const community = findCommunity(params)
    if (community instanceof Response) {
      return community
    }
    // ログインしていれば、自分との関係も入れる
    return HttpResponse.json(toDetail(community, findLoggedInUser(cookies)?.id))
  }),

  /**
   * コミュニティを編集する（PATCH /api/communities/:communityId）。オーナーだけ。アーカイブ中はできない。
   */
  http.patch('/api/communities/:communityId', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const community = findCommunity(params)
    if (community instanceof Response) {
      return community
    }
    if (community.ownerId !== user.id) {
      return forbidden()
    }
    if (community.archivedAt !== null) {
      return problem(409, 'COMMUNITY_ARCHIVED', 'アーカイブ中のコミュニティは編集できません。先に元に戻してください。')
    }
    const input = validateCommunityInput((await request.json()) as Partial<CommunityInput>)
    if (input instanceof Response) {
      return input
    }
    community.name = input.name
    community.description = input.description
    return HttpResponse.json(toDetail(community, user.id))
  }),

  /**
   * コミュニティをアーカイブする（PUT /api/communities/:communityId/archive）。オーナーだけ。
   * 申請中の参加申請は、すべて却下にする（要件定義書 5.9）。何度呼んでも結果は同じ。
   */
  http.put('/api/communities/:communityId/archive', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const community = findCommunity(params)
    if (community instanceof Response) {
      return community
    }
    if (community.ownerId !== user.id) {
      return forbidden()
    }
    if (community.archivedAt === null) {
      community.archivedAt = new Date().toISOString()
      for (const r of db.joinRequests) {
        if (r.communityId === community.id && r.status === 'pending') {
          r.status = 'rejected'
        }
      }
    }
    return HttpResponse.json(toDetail(community, user.id))
  }),

  /**
   * アーカイブを元に戻す（DELETE /api/communities/:communityId/archive）。オーナーだけ。何度呼んでも結果は同じ。
   */
  http.delete('/api/communities/:communityId/archive', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const community = findCommunity(params)
    if (community instanceof Response) {
      return community
    }
    if (community.ownerId !== user.id) {
      return forbidden()
    }
    community.archivedAt = null
    return HttpResponse.json(toDetail(community, user.id))
  }),

  /**
   * 参加申請の一覧（GET /api/communities/:communityId/join-requests）。オーナーと運営スタッフだけ。
   * 申請中のものを、申請した日時の古い順に返す。
   */
  http.get('/api/communities/:communityId/join-requests', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const community = findCommunity(params)
    if (community instanceof Response) {
      return community
    }
    const role = findRole(community.id, user.id)
    if (role !== 'owner' && role !== 'staff') {
      return forbidden()
    }
    const items = db.joinRequests
      .filter((r) => r.communityId === community.id && r.status === 'pending')
      .sort((a, b) => a.requestedAt.localeCompare(b.requestedAt))
      .map(toJoinRequest)
    return HttpResponse.json(paginate(items, parsePage(request)))
  }),

  /**
   * 参加を申請する（POST /api/communities/:communityId/join-requests）。
   */
  http.post('/api/communities/:communityId/join-requests', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const community = findCommunity(params)
    if (community instanceof Response) {
      return community
    }
    if (community.archivedAt !== null) {
      return problem(409, 'COMMUNITY_ARCHIVED', 'アーカイブ中のコミュニティには、参加を申請できません。')
    }
    if (findRole(community.id, user.id) !== null) {
      return problem(409, 'ALREADY_MEMBER', 'すでにこのコミュニティのメンバーです。')
    }
    if (db.joinRequests.some((r) => r.communityId === community.id && r.userId === user.id && r.status === 'pending')) {
      return problem(409, 'JOIN_REQUEST_PENDING', 'すでに参加を申請しています。承認されるまでお待ちください。')
    }
    const joinRequest: MockJoinRequest = {
      id: nextId(db.joinRequests),
      communityId: community.id,
      userId: user.id,
      status: 'pending',
      requestedAt: new Date().toISOString(),
    }
    db.joinRequests.push(joinRequest)
    return HttpResponse.json(toJoinRequest(joinRequest), { status: 201 })
  }),

  /**
   * 参加申請を承認・却下する（PATCH /api/communities/:communityId/join-requests/:joinRequestId）。オーナーと運営スタッフだけ。
   */
  http.patch('/api/communities/:communityId/join-requests/:joinRequestId', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const community = findCommunity(params)
    if (community instanceof Response) {
      return community
    }
    const role = findRole(community.id, user.id)
    if (role !== 'owner' && role !== 'staff') {
      return forbidden()
    }
    const joinRequest = db.joinRequests.find(
      (r) => r.id === Number(params.joinRequestId) && r.communityId === community.id,
    )
    if (!joinRequest) {
      return problem(404, 'NOT_FOUND', '参加申請が見つかりません。')
    }
    const body = (await request.json()) as { status?: unknown }
    if (body.status !== 'approved' && body.status !== 'rejected') {
      return problem(400, 'VALIDATION_ERROR', '入力内容に誤りがあります。', {
        errors: [{ field: 'status', message: 'approved か rejected を指定してください。' }],
      })
    }
    // すでに処理されている（ほかの運営スタッフが先に処理した、アーカイブで自動で却下された）
    if (joinRequest.status !== 'pending') {
      return problem(409, 'JOIN_REQUEST_ALREADY_DECIDED', 'この参加申請は、すでに承認または却下されています。')
    }
    joinRequest.status = body.status
    // 承認したら、メンバーにする
    if (body.status === 'approved') {
      db.memberships.push({
        communityId: community.id,
        userId: joinRequest.userId,
        role: 'member',
        joinedAt: new Date().toISOString(),
      })
    }
    return HttpResponse.json(toJoinRequest(joinRequest))
  }),

  /**
   * メンバーの一覧（GET /api/communities/:communityId/members）。オーナーと運営スタッフだけ。
   * 立場の順（オーナー → 運営スタッフ → メンバー）、同じ立場なら参加した日時の古い順。
   * group=managers ならオーナーと運営スタッフだけ、group=members なら一般のメンバーだけ（Issue #45）。
   */
  http.get('/api/communities/:communityId/members', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const community = findCommunity(params)
    if (community instanceof Response) {
      return community
    }
    const role = findRole(community.id, user.id)
    if (role !== 'owner' && role !== 'staff') {
      return forbidden()
    }
    const group = new URL(request.url).searchParams.get('group')
    const items = db.memberships
      .filter((m) => m.communityId === community.id)
      .filter((m) => (group === 'managers' ? m.role !== 'member' : group === 'members' ? m.role === 'member' : true))
      .sort((a, b) => ROLE_ORDER[a.role] - ROLE_ORDER[b.role] || a.joinedAt.localeCompare(b.joinedAt))
      .map(toMember)
    return HttpResponse.json(paginate(items, parsePage(request)))
  }),

  /**
   * 運営スタッフに任命・解除する（PATCH /api/communities/:communityId/members/:userId）。オーナーだけ。
   */
  http.patch('/api/communities/:communityId/members/:userId', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const community = findCommunity(params)
    if (community instanceof Response) {
      return community
    }
    if (community.ownerId !== user.id) {
      return forbidden()
    }
    const membership = db.memberships.find((m) => m.communityId === community.id && m.userId === Number(params.userId))
    if (!membership) {
      return problem(404, 'NOT_FOUND', 'メンバーが見つかりません。')
    }
    const body = (await request.json()) as { role?: unknown }
    if (body.role !== 'staff' && body.role !== 'member') {
      return problem(400, 'VALIDATION_ERROR', '入力内容に誤りがあります。', {
        errors: [{ field: 'role', message: 'staff か member を指定してください。' }],
      })
    }
    if (membership.role === 'owner') {
      return problem(409, 'CANNOT_CHANGE_OWNER', 'オーナーの立場は変えられません。')
    }
    membership.role = body.role
    return HttpResponse.json(toMember(membership))
  }),

  /**
   * メンバーを除名する（DELETE /api/communities/:communityId/members/:userId）。オーナーだけ。
   * 除名された人の、開催前のイベントへの申込みは、すべて取り消す（要件定義書 5.4.1）。
   */
  http.delete('/api/communities/:communityId/members/:userId', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const community = findCommunity(params)
    if (community instanceof Response) {
      return community
    }
    if (community.ownerId !== user.id) {
      return forbidden()
    }
    const targetId = Number(params.userId)
    const membership = db.memberships.find((m) => m.communityId === community.id && m.userId === targetId)
    if (!membership) {
      return problem(404, 'NOT_FOUND', 'メンバーが見つかりません。')
    }
    if (membership.role === 'owner') {
      return problem(409, 'CANNOT_REMOVE_OWNER', 'オーナーは除名できません。')
    }
    db.memberships = db.memberships.filter((m) => m !== membership)
    cancelUpcomingApplications(targetId, user.id, community.id)
    return new HttpResponse(null, { status: 204 })
  }),

  /**
   * コミュニティを退会する（DELETE /api/communities/:communityId/membership）。メンバーと運営スタッフ。
   * 開催前のイベントへの申込みは、すべて取り消す（要件定義書 5.4）。
   */
  http.delete('/api/communities/:communityId/membership', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const community = findCommunity(params)
    if (community instanceof Response) {
      return community
    }
    const role = findRole(community.id, user.id)
    if (role === null) {
      return problem(409, 'NOT_MEMBER', 'このコミュニティのメンバーではありません。')
    }
    if (role === 'owner') {
      return problem(409, 'OWNER_CANNOT_LEAVE', 'オーナーは退会できません。コミュニティをアーカイブしてください。')
    }
    db.memberships = db.memberships.filter((m) => !(m.communityId === community.id && m.userId === user.id))
    cancelUpcomingApplications(user.id, user.id, community.id)
    return new HttpResponse(null, { status: 204 })
  }),
]

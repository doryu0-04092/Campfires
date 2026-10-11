/**
 * 【役割】イベントと申込みの偽の API（docs/api/openapi.yaml の events と applications）。
 * 【なぜ必要か】バックエンドができるまでの間、イベントの画面の動きを確かめるため。
 * 申込み、キャンセル、繰り上げ、承認・却下、お知らせのルールは、要件定義書 5.2、5.3、5.10、5.12 のとおり（src/mocks/rules.ts）。
 */

import { delay, http, HttpResponse } from 'msw'
import type { Application, EventDetail, EventInput, MyApplication } from '../../types/event'
import { db, nextId } from '../db'
import type { MockCommunity } from '../db/communities'
import type { MockApplication, MockEvent } from '../db/events'
import {
  appliedOf,
  approveAllPending,
  approveApplication,
  cancelApplication,
  countApplied,
  countPending,
  deleteEvent,
  isDeadlinePassed,
  isEnded,
  isMemberOf,
  isStarted,
  pendingOf,
  promoteWaitlist,
  rejectApplication,
  waitlistOf,
} from '../rules'
import { searchEvents, toEventSummary } from '../search'
import { checkCsrf, findLoggedInUser, findRole, paginate, parsePage, problem, requireLogin, toUserRef } from './common'
import { Validator } from './validation'

type Params = Record<string, string | readonly string[] | undefined>

/**
 * 【何のため】URL の :communityId と :eventId から、コミュニティとイベントを探す。
 * イベントが指定したコミュニティのものでないときも、見つからない（404）とする。
 */
function findEvent(params: Params): { community: MockCommunity; event: MockEvent } | Response {
  const community = db.communities.find((c) => c.id === Number(params.communityId))
  const event = db.events.find((e) => e.id === Number(params.eventId) && e.communityId === community?.id)
  if (!community || !event) {
    return problem(404, 'NOT_FOUND', 'イベントが見つかりません。')
  }
  return { community, event }
}

/**
 * 【何のため】申込みの状況を、画面に返す形にする（キャンセル待ちなら、何番目かも入れる）。
 */
function toMyApplication(application: MockApplication): MyApplication {
  const position = waitlistOf(application.eventId).indexOf(application)
  return {
    status: application.status,
    waitlistPosition: application.status === 'waitlisted' ? position + 1 : null,
    appliedAt: application.appliedAt,
  }
}

/**
 * 【何のため】イベントの詳細（EventDetail）を作る。見ている人（userId）との関係も入れる。
 */
function toDetail(community: MockCommunity, event: MockEvent, userId: number | undefined): EventDetail {
  const mine = userId === undefined ? undefined : db.applications.find((a) => a.eventId === event.id && a.userId === userId)
  return {
    ...toEventSummary(db, event),
    description: event.description,
    location: event.location,
    applicationDeadline: event.applicationDeadline,
    waitlistCount: waitlistOf(event.id).length,
    pendingCount: countPending(event.id),
    memberApproval: event.memberApproval,
    nonMemberApproval: event.nonMemberApproval,
    communityArchivedAt: community.archivedAt,
    myRole: findRole(community.id, userId),
    myApplication: mine ? toMyApplication(mine) : null,
    createdAt: event.createdAt,
  }
}

/**
 * 【何のため】日時の文字が、正しい日時かどうかを確かめる。正しければ Date を、正しくなければ null を返す。
 */
function parseDate(value: unknown): Date | null {
  if (typeof value !== 'string' || value === '') {
    return null
  }
  const date = new Date(value)
  // getTime() が数でない（NaN）なら、正しい日時ではない
  return Number.isNaN(date.getTime()) ? null : date
}

/**
 * 【何のため】イベントの作成・編集で送られてきたデータをチェックする（docs/api/openapi.yaml の EventInput）。
 * 日時の前後関係：今 < 開始日時、申込み締め切り日時 < 開始日時 < 終了日時
 * 承認の方法が送られてこなければ、作成では最初の値（メンバーは自動、メンバー以外は受け付けない）、編集では今の値にする。
 * @param current 編集のときは今のイベント。作成のときは undefined
 */
function validateEventInput(body: Partial<EventInput>, current?: MockEvent): EventInput | Response {
  const validator = new Validator()
  const title = validator.text('title', body.title, 'タイトル', 1, 100)
  const description = validator.text('description', body.description, '説明', 0, 5000)
  const location = validator.text('location', body.location, '場所', 1, 200)

  const capacity = body.capacity
  if (typeof capacity !== 'number' || !Number.isInteger(capacity) || capacity < 1 || capacity > 1000) {
    validator.errors.push({ field: 'capacity', message: '定員は1〜1000人の整数にしてください。' })
  }

  const startAt = parseDate(body.startAt)
  const endAt = parseDate(body.endAt)
  const deadline = parseDate(body.applicationDeadline)
  if (!startAt) {
    validator.errors.push({ field: 'startAt', message: '開始日時を入力してください。' })
  } else if (startAt <= new Date()) {
    validator.errors.push({ field: 'startAt', message: '開始日時は、今より後にしてください。' })
  }
  if (!endAt) {
    validator.errors.push({ field: 'endAt', message: '終了日時を入力してください。' })
  } else if (startAt && endAt <= startAt) {
    validator.errors.push({ field: 'endAt', message: '終了日時は、開始日時より後にしてください。' })
  }
  if (!deadline) {
    validator.errors.push({ field: 'applicationDeadline', message: '申込み締め切り日時を入力してください。' })
  } else if (startAt && deadline >= startAt) {
    validator.errors.push({ field: 'applicationDeadline', message: '申込み締め切り日時は、開始日時より前にしてください。' })
  }

  // 承認の方法（送られてこなければ、作成では最初の値、編集では今の値）
  const memberApproval = body.memberApproval ?? current?.memberApproval ?? 'auto'
  const nonMemberApproval = body.nonMemberApproval ?? current?.nonMemberApproval ?? 'closed'
  if (memberApproval !== 'auto' && memberApproval !== 'manual') {
    validator.errors.push({ field: 'memberApproval', message: 'メンバーの承認の方法は、自動か手動を選んでください。' })
  }
  if (nonMemberApproval !== 'auto' && nonMemberApproval !== 'manual' && nonMemberApproval !== 'closed') {
    validator.errors.push({
      field: 'nonMemberApproval',
      message: 'メンバー以外の承認の方法は、自動・手動・受け付けないのどれかを選んでください。',
    })
  }

  if (validator.hasErrors()) {
    return problem(400, 'VALIDATION_ERROR', '入力内容に誤りがあります。', { errors: validator.errors })
  }
  return {
    title,
    description,
    location,
    capacity: capacity as number,
    startAt: startAt!.toISOString(),
    endAt: endAt!.toISOString(),
    applicationDeadline: deadline!.toISOString(),
    memberApproval,
    nonMemberApproval,
  }
}

// 権限がないときの返事（403）
const forbidden = () => problem(403, 'FORBIDDEN', 'この操作をする権限がありません。')

/**
 * 【何のため】オーナーか運営スタッフかどうか。
 */
function isManager(communityId: number, userId: number): boolean {
  const role = findRole(communityId, userId)
  return role === 'owner' || role === 'staff'
}

export const eventHandlers = [
  /**
   * 自分が申し込んだイベントの一覧（GET /api/me/events）。
   * 開催前のイベント（キャンセル待ちを含む）を、開始日時の近い順に全件返す（要件定義書 5.8）。
   */
  http.get('/api/me/events', async ({ cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const items = db.applications
      .filter((a) => a.userId === user.id)
      // 申込みと、そのイベントを組にする
      .map((application) => ({ application, event: db.events.find((e) => e.id === application.eventId)! }))
      // 終了したイベントは出さない
      .filter(({ event }) => !isEnded(event))
      // 開始日時の近い順
      .sort((a, b) => a.event.startAt.localeCompare(b.event.startAt))
      .map(({ application, event }) => ({ ...toEventSummary(db, event), myApplication: toMyApplication(application) }))
    return HttpResponse.json({ items })
  }),

  /**
   * イベントの一覧・検索（GET /api/events）。例：GET /api/events?q=Java+初心者&page=2
   */
  http.get('/api/events', async ({ request }) => {
    // 本物の通信に近づけるため、0.3秒待ってから返事をする（読み込み中の表示を確かめられる）
    await delay(300)
    const query = new URL(request.url).searchParams.get('q') ?? ''
    return HttpResponse.json(searchEvents(db, query, parsePage(request), new Date()))
  }),

  /**
   * コミュニティのイベントの一覧（GET /api/communities/:communityId/events）。
   * status=upcoming（初期値）：開催前を開始日時の近い順。status=past：終了済みを開始日時の新しい順。
   */
  http.get('/api/communities/:communityId/events', async ({ request, params }) => {
    await delay(300)
    const community = db.communities.find((c) => c.id === Number(params.communityId))
    if (!community) {
      return problem(404, 'NOT_FOUND', 'コミュニティが見つかりません。')
    }
    const isPast = new URL(request.url).searchParams.get('status') === 'past'
    const events = db.events
      .filter((e) => e.communityId === community.id && isEnded(e) === isPast)
      .sort((a, b) => (isPast ? b.startAt.localeCompare(a.startAt) : a.startAt.localeCompare(b.startAt)))
      .map((e) => toEventSummary(db, e))
    return HttpResponse.json(paginate(events, parsePage(request)))
  }),

  /**
   * イベントを作成する（POST /api/communities/:communityId/events）。オーナーと運営スタッフだけ。
   */
  http.post('/api/communities/:communityId/events', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const community = db.communities.find((c) => c.id === Number(params.communityId))
    if (!community) {
      return problem(404, 'NOT_FOUND', 'コミュニティが見つかりません。')
    }
    if (!isManager(community.id, user.id)) {
      return forbidden()
    }
    if (community.archivedAt !== null) {
      return problem(409, 'COMMUNITY_ARCHIVED', 'アーカイブ中のコミュニティでは、イベントを作れません。')
    }
    const input = validateEventInput((await request.json()) as Partial<EventInput>)
    if (input instanceof Response) {
      return input
    }
    const event: MockEvent = { id: nextId(db.events), communityId: community.id, ...input, createdAt: new Date().toISOString() }
    db.events.push(event)
    return HttpResponse.json(toDetail(community, event, user.id), {
      status: 201,
      headers: { Location: `/api/communities/${community.id}/events/${event.id}` },
    })
  }),

  /**
   * イベントの詳細（GET /api/communities/:communityId/events/:eventId）。ログインしていなくても見られる。
   */
  http.get('/api/communities/:communityId/events/:eventId', async ({ params, cookies }) => {
    await delay(300)
    const found = findEvent(params)
    if (found instanceof Response) {
      return found
    }
    return HttpResponse.json(toDetail(found.community, found.event, findLoggedInUser(cookies)?.id))
  }),

  /**
   * イベントを編集する（PATCH /api/communities/:communityId/events/:eventId）。オーナーと運営スタッフだけ。
   */
  http.patch('/api/communities/:communityId/events/:eventId', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const found = findEvent(params)
    if (found instanceof Response) {
      return found
    }
    const { community, event } = found
    if (!isManager(community.id, user.id)) {
      return forbidden()
    }
    if (isEnded(event)) {
      return problem(409, 'EVENT_ENDED', '終了したイベントは編集できません。')
    }
    const input = validateEventInput((await request.json()) as Partial<EventInput>, event)
    if (input instanceof Response) {
      return input
    }
    // 定員を、今の申込み済みの人数より少なくできない（要件定義書 5.2）
    const applied = countApplied(event.id)
    if (input.capacity < applied) {
      return problem(409, 'CAPACITY_BELOW_APPLIED', `定員を、今の申込み済みの人数（${applied}人）より少なくできません。`)
    }
    // 変える前の承認の方法を覚えておく
    const before = { member: event.memberApproval, nonMember: event.nonMemberApproval }
    Object.assign(event, input)
    // 締め切り前に定員を増やしたときは、増えた分だけキャンセル待ちの人を繰り上げる
    promoteWaitlist(event)
    // 承認の方法を「自動」に変えたときは、承認待ちを申し込んだ順にまとめて承認する（要件定義書 5.12）
    if (before.member !== 'auto' && event.memberApproval === 'auto') {
      approveAllPending(event, true)
    }
    if (before.nonMember !== 'auto' && event.nonMemberApproval === 'auto') {
      approveAllPending(event, false)
    }
    return HttpResponse.json(toDetail(community, event, user.id))
  }),

  /**
   * イベントを削除する（DELETE /api/communities/:communityId/events/:eventId）。オーナーと運営スタッフだけ。
   * 終了したイベントは削除できない。申し込んでいた人にお知らせを送る。
   */
  http.delete('/api/communities/:communityId/events/:eventId', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const found = findEvent(params)
    if (found instanceof Response) {
      return found
    }
    if (!isManager(found.community.id, user.id)) {
      return forbidden()
    }
    if (isEnded(found.event)) {
      return problem(409, 'EVENT_ENDED', '終了したイベントは、参加の記録として残すため削除できません。')
    }
    deleteEvent(found.event, user.id)
    return new HttpResponse(null, { status: 204 })
  }),

  /**
   * 申込者の一覧（GET /api/communities/:communityId/events/:eventId/applications）。オーナーと運営スタッフだけ。
   * 承認待ちの人を申し込んだ順、申込み済みの人を席を取った順、キャンセル待ちの人を順番どおり、の順に並べる。
   * 却下された人は出さない。
   */
  http.get('/api/communities/:communityId/events/:eventId/applications', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const found = findEvent(params)
    if (found instanceof Response) {
      return found
    }
    if (!isManager(found.community.id, user.id)) {
      return forbidden()
    }
    const id = found.event.id
    const items: Application[] = [...pendingOf(id), ...appliedOf(id), ...waitlistOf(id)].map((a) => ({
      user: toUserRef(a.userId),
      ...toMyApplication(a),
    }))
    return HttpResponse.json(paginate(items, parsePage(request)))
  }),

  /**
   * イベントに申し込む（POST /api/communities/:communityId/events/:eventId/applications）。
   * だれが申し込めるか、すぐに席を取るかは、イベントの承認の方法で決まる（要件定義書 5.12）。
   * 自動なら、空きがあれば申込み済み、満席ならキャンセル待ち。手動なら承認待ち。
   */
  http.post('/api/communities/:communityId/events/:eventId/applications', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const found = findEvent(params)
    if (found instanceof Response) {
      return found
    }
    const { event } = found
    // メンバーかどうかで、使う承認の方法が変わる
    const approval = isMemberOf(event, user.id) ? event.memberApproval : event.nonMemberApproval
    if (approval === 'closed') {
      return problem(403, 'NOT_MEMBER', 'このイベントに申し込むには、コミュニティのメンバーになる必要があります。')
    }
    const existing = db.applications.find((a) => a.eventId === event.id && a.userId === user.id)
    if (existing?.status === 'rejected') {
      // 却下された人は、同じイベントに申し込み直せない
      return problem(409, 'APPLICATION_REJECTED', 'このイベントへの申込みは、却下されています。')
    }
    if (existing) {
      return problem(409, 'ALREADY_APPLIED', 'すでに申し込んでいます。')
    }
    if (isDeadlinePassed(event)) {
      return problem(409, 'APPLICATION_DEADLINE_PASSED', '申込みの締め切りを過ぎています。')
    }
    // 手動なら承認待ち（席は取らない）。自動なら、空きがあれば申込み済み、なければキャンセル待ち（F-15）
    const now = new Date().toISOString()
    const status = approval === 'manual' ? 'pending' : countApplied(event.id) < event.capacity ? 'applied' : 'waitlisted'
    const application: MockApplication = {
      id: nextId(db.applications),
      eventId: event.id,
      userId: user.id,
      status,
      appliedAt: now,
      queuedAt: status === 'pending' ? null : now,
    }
    db.applications.push(application)
    return HttpResponse.json(toMyApplication(application), { status: 201 })
  }),

  /**
   * 承認待ちの申込みを、承認・却下する（PATCH /api/communities/:communityId/events/:eventId/applications/:userId）。
   * オーナーと運営スタッフだけ。イベントの開始日時まで（要件定義書 5.12）。
   */
  http.patch('/api/communities/:communityId/events/:eventId/applications/:userId', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const found = findEvent(params)
    if (found instanceof Response) {
      return found
    }
    const { community, event } = found
    if (!isManager(community.id, user.id)) {
      return forbidden()
    }
    const body = (await request.json()) as { status?: unknown }
    if (body.status !== 'approved' && body.status !== 'rejected') {
      return problem(400, 'VALIDATION_ERROR', '入力内容に誤りがあります。', {
        errors: [{ field: 'status', message: 'approved か rejected を指定してください。' }],
      })
    }
    const application = db.applications.find((a) => a.eventId === event.id && a.userId === Number(params.userId))
    if (!application) {
      return problem(404, 'NOT_FOUND', '申込みが見つかりません。')
    }
    if (isStarted(event)) {
      return problem(409, 'EVENT_STARTED', '開始したイベントの申込みは、承認・却下できません。')
    }
    if (application.status !== 'pending') {
      return problem(409, 'APPLICATION_ALREADY_DECIDED', 'この申込みは、すでに承認または却下されています。')
    }
    if (body.status === 'approved') {
      approveApplication(event, application)
    } else {
      rejectApplication(event, application)
    }
    return HttpResponse.json({ user: toUserRef(application.userId), ...toMyApplication(application) })
  }),

  /**
   * 自分の申込みを取り消す（DELETE /api/communities/:communityId/events/:eventId/my-application）。
   * 締め切りの前後で、繰り上げとお知らせの動きが変わる（src/mocks/rules.ts の cancelApplication）。
   */
  http.delete('/api/communities/:communityId/events/:eventId/my-application', async ({ request, params, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const found = findEvent(params)
    if (found instanceof Response) {
      return found
    }
    if (isEnded(found.event)) {
      return problem(409, 'EVENT_ENDED', '終了したイベントの申込みは、取り消せません。')
    }
    // 却下された申込みは、取り消せない
    const mine = db.applications.find((a) => a.eventId === found.event.id && a.userId === user.id && a.status !== 'rejected')
    if (!mine) {
      return problem(409, 'NOT_APPLIED', 'このイベントには申し込んでいません。')
    }
    cancelApplication(mine, user.id)
    return new HttpResponse(null, { status: 204 })
  }),
]

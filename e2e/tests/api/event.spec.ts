/**
 * 【役割】イベントの機能（作成・編集・削除、申込み、キャンセル待ちと繰り上げ、承認・却下、お知らせ）を、
 * API とデータベースだけで、外から確かめる E2E テスト。
 * 【なぜ必要か】席の数え方や、繰り上げ、お知らせのように、データベースの中で決まる動きが、本番の通り道で正しく動くかを確かめるため。
 *
 * 登場する人：owner（コミュニティのオーナー）、member（コミュニティのメンバー）、outsider（メンバーではない人）。
 */
import { expect, test } from '@playwright/test'

import { eventInput, json, type ApiSession } from '../../support/api'
import { account, runId, sessionOf } from '../../support/accounts'

interface MyApplication {
  status: string
  waitlistPosition: number | null
}
interface EventDetail {
  id: number
  remainingSeats: number
  waitlistCount: number
  pendingCount: number
  myApplication: MyApplication | null
}
interface Page<T> {
  items: T[]
}
interface Notification {
  id: number
  type: string
  eventTitle: string
  read: boolean
}

test.describe.serial('イベントと申込み', () => {
  let owner: ApiSession
  let member: ApiSession
  let outsider: ApiSession
  let communityId: number

  /** 【何のため】イベントを作り、その番号を返す */
  async function createEvent(overrides: Record<string, unknown>): Promise<number> {
    const created = await json<{ id: number }>(
      await owner.post(`/api/communities/${communityId}/events`, eventInput(overrides)),
      201,
    )
    return created.id
  }

  /** 【何のため】その人から見た、イベントの詳細を読む */
  async function detail(session: ApiSession, eventId: number): Promise<EventDetail> {
    return json<EventDetail>(await session.get(`/api/communities/${communityId}/events/${eventId}`))
  }

  /** 【何のため】その人のお知らせの一覧を読む */
  async function notifications(session: ApiSession): Promise<Notification[]> {
    return (await json<Page<Notification>>(await session.get('/api/notifications'))).items
  }

  // 準備：コミュニティを作り、member をメンバーにする（ここは、テストしたい部分ではないので、まとめて行う）
  test.beforeAll(async () => {
    owner = await sessionOf('owner')
    member = await sessionOf('member')
    outsider = await sessionOf('outsider')
    const community = await json<{ id: number }>(
      await owner.post('/api/communities', { name: `E2E API イベント用 ${runId()}`, description: '' }),
      201,
    )
    communityId = community.id
    const request = await json<{ id: number }>(await member.post(`/api/communities/${communityId}/join-requests`), 201)
    await json(await owner.patch(`/api/communities/${communityId}/join-requests/${request.id}`, { status: 'approved' }))
  })

  test.afterAll(async () => {
    await Promise.all([owner.dispose(), member.dispose(), outsider.dispose()])
  })

  // 【何のため】入力のチェック（日時の前後、定員）が、本番の通り道でも効くことを確かめる
  test('日時や定員がおかしいイベントは作れない（400）', async () => {
    const badDeadline = await owner.post(
      `/api/communities/${communityId}/events`,
      eventInput({ applicationDeadline: eventInput().endAt }),
    )
    expect(badDeadline.status()).toBe(400)
    const badCapacity = await owner.post(`/api/communities/${communityId}/events`, eventInput({ capacity: 0 }))
    expect(badCapacity.status()).toBe(400)
    // 過去の日時のイベントは作れない
    const past = await owner.post(
      `/api/communities/${communityId}/events`,
      eventInput({ startAt: '2020-01-01T10:00:00Z', endAt: '2020-01-01T12:00:00Z', applicationDeadline: '2019-12-31T10:00:00Z' }),
    )
    expect(past.status()).toBe(400)
  })

  // 【何のため】満席のときはキャンセル待ちになり、キャンセルが出たら、順番どおりに繰り上がることを確かめる
  test('満席ならキャンセル待ち、キャンセルで繰り上がる', async () => {
    const eventId = await createEvent({ title: `E2E 定員1 ${runId()}`, capacity: 1 })

    const first = await json<{ status: string }>(
      await member.post(`/api/communities/${communityId}/events/${eventId}/applications`),
      201,
    )
    expect(first.status).toBe('applied')
    // 同じ人が二重に申し込むことはできない
    expect((await member.post(`/api/communities/${communityId}/events/${eventId}/applications`)).status()).toBe(409)

    const second = await json<{ status: string; waitlistPosition: number }>(
      await outsider.post(`/api/communities/${communityId}/events/${eventId}/applications`),
      201,
    )
    expect(second.status).toBe('waitlisted')
    expect(second.waitlistPosition).toBe(1)
    expect((await detail(owner, eventId)).remainingSeats).toBe(0)

    // 申込み済みの人がキャンセルすると、キャンセル待ちの1番目の人が、申込み済みになる
    expect((await member.delete(`/api/communities/${communityId}/events/${eventId}/my-application`)).status()).toBe(204)
    const promoted = await detail(outsider, eventId)
    expect(promoted.myApplication?.status).toBe('applied')
    expect(promoted.waitlistCount).toBe(0)
    expect((await detail(member, eventId)).myApplication).toBeNull()
  })

  // 【何のため】手動の承認：申込みは承認待ちになり、承認で席を取り、本人にお知らせが届くことを確かめる
  test('手動の承認：承認すると席を取り、お知らせが届く', async () => {
    const title = `E2E 手動承認 ${runId()}`
    const eventId = await createEvent({ title, memberApproval: 'manual', nonMemberApproval: 'closed' })

    const applied = await json<{ status: string }>(
      await member.post(`/api/communities/${communityId}/events/${eventId}/applications`),
      201,
    )
    expect(applied.status).toBe('pending')
    // メンバー以外は「受け付けない」なので、申し込めない
    const closed = await outsider.post(`/api/communities/${communityId}/events/${eventId}/applications`)
    expect(closed.status()).toBe(403)
    expect((await closed.json()).code).toBe('NOT_MEMBER')
    // 申し込んだ本人は、承認・却下できない
    expect(
      (
        await member.patch(`/api/communities/${communityId}/events/${eventId}/applications/${account('member').id}`, {
          status: 'approved',
        })
      ).status(),
    ).toBe(403)

    const before = await detail(owner, eventId)
    expect(before.pendingCount).toBe(1)
    expect(before.remainingSeats).toBe(5)

    await json(
      await owner.patch(`/api/communities/${communityId}/events/${eventId}/applications/${account('member').id}`, {
        status: 'approved',
      }),
    )
    const after = await detail(member, eventId)
    expect(after.myApplication?.status).toBe('applied')
    expect(after.remainingSeats).toBe(4)

    const approvedNotice = (await notifications(member)).find((n) => n.eventTitle === title)
    expect(approvedNotice?.type).toBe('application_approved')
  })

  // 【何のため】手動の承認：却下すると、本人にお知らせが届き、もう取り消せないことを確かめる
  test('手動の承認：却下すると、お知らせが届く', async () => {
    const title = `E2E 却下 ${runId()}`
    const eventId = await createEvent({ title, memberApproval: 'manual', nonMemberApproval: 'manual' })
    await json(await outsider.post(`/api/communities/${communityId}/events/${eventId}/applications`), 201)
    await json(
      await owner.patch(`/api/communities/${communityId}/events/${eventId}/applications/${account('outsider').id}`, {
        status: 'rejected',
      }),
    )
    expect((await detail(outsider, eventId)).myApplication?.status).toBe('rejected')
    // 却下された申込みは、取り消せない
    expect(
      (await outsider.delete(`/api/communities/${communityId}/events/${eventId}/my-application`)).status(),
    ).toBe(409)
    const notice = (await notifications(outsider)).find((n) => n.eventTitle === title)
    expect(notice?.type).toBe('application_rejected')
  })

  // 【何のため】手動から自動に変えると、承認待ちの人が、まとめて承認されることを確かめる
  test('手動から自動に変えると、承認待ちがまとめて承認される', async () => {
    const eventId = await createEvent({ title: `E2E 自動へ ${runId()}`, memberApproval: 'manual', nonMemberApproval: 'manual' })
    await json(await member.post(`/api/communities/${communityId}/events/${eventId}/applications`), 201)
    await json(await outsider.post(`/api/communities/${communityId}/events/${eventId}/applications`), 201)
    expect((await detail(owner, eventId)).pendingCount).toBe(2)

    await json(
      await owner.patch(
        `/api/communities/${communityId}/events/${eventId}`,
        eventInput({ title: `E2E 自動へ ${runId()}`, memberApproval: 'auto', nonMemberApproval: 'auto' }),
      ),
    )
    const after = await detail(owner, eventId)
    expect(after.pendingCount).toBe(0)
    expect(after.remainingSeats).toBe(3)
    expect((await detail(member, eventId)).myApplication?.status).toBe('applied')
  })

  // 【何のため】イベントを削除すると、申し込んでいた人にお知らせが届くことを確かめる
  test('削除すると、申し込んでいた人にお知らせが届く', async () => {
    const title = `E2E 削除 ${runId()}`
    const eventId = await createEvent({ title })
    await json(await member.post(`/api/communities/${communityId}/events/${eventId}/applications`), 201)
    // ふつうのメンバーは削除できない
    expect((await member.delete(`/api/communities/${communityId}/events/${eventId}`)).status()).toBe(403)

    expect((await owner.delete(`/api/communities/${communityId}/events/${eventId}`)).status()).toBe(204)
    expect((await owner.get(`/api/communities/${communityId}/events/${eventId}`)).status()).toBe(404)
    const notice = (await notifications(member)).find((n) => n.eventTitle === title)
    expect(notice?.type).toBe('event_deleted')
  })

  // 【何のため】自分の参加予定のイベントの一覧に、申し込んだイベントが出ることを確かめる
  test('参加予定のイベントの一覧に出る', async () => {
    const eventId = await createEvent({ title: `E2E 参加予定 ${runId()}` })
    await json(await member.post(`/api/communities/${communityId}/events/${eventId}/applications`), 201)
    const mine = await json<Page<{ id: number }>>(await member.get('/api/me/events'))
    expect(mine.items.map((e) => e.id)).toContain(eventId)
  })

  // 【何のため】お知らせの未読の数と、既読にする操作を確かめる
  test('お知らせを既読にする', async () => {
    const unread = async () => (await json<{ count: number }>(await member.get('/api/notifications/unread-count'))).count
    expect(await unread()).toBeGreaterThan(0)

    const first = (await notifications(member)).find((n) => !n.read)!
    await json(await member.put(`/api/notifications/${first.id}/read`))
    // ほかの人のお知らせは、既読にできない（見つからない扱い）
    expect((await outsider.put(`/api/notifications/${first.id}/read`)).status()).toBe(404)

    expect((await member.post('/api/notifications/read-all')).status()).toBe(204)
    expect(await unread()).toBe(0)
  })
})

/**
 * 【役割】コミュニティの機能（作成・検索・参加の申請と承認・運営スタッフ・除名・退会・アーカイブ）を、
 * API とデータベースだけで、外から確かめる E2E テスト。
 * 【なぜ必要か】画面を通さずに、API の決まり（だれが何をできるか、データがどう変わるか）が、本番の通り道で守られているかを確かめるため。
 *
 * 登場する人：owner（作る人）、member（参加する人）、outsider（参加しない人）。準備（global-setup）で作った利用者を使う。
 * test.describe.serial は「上から順番に行い、1つ失敗したら残りは行わない」という書き方（前のテストの結果を、次のテストで使うため）。
 */
import { expect, test } from '@playwright/test'

import { json, type ApiSession } from '../../support/api'
import { account, runId, sessionOf } from '../../support/accounts'

interface Page<T> {
  items: T[]
  totalCount: number
}
interface JoinRequest {
  id: number
  user: { userId: number; nickname: string }
  status: string
}

test.describe.serial('コミュニティ', () => {
  let owner: ApiSession
  let member: ApiSession
  let outsider: ApiSession
  let communityId: number
  const name = `E2E API コミュニティ ${runId()}`

  test.beforeAll(async () => {
    owner = await sessionOf('owner')
    member = await sessionOf('member')
    outsider = await sessionOf('outsider')
  })

  test.afterAll(async () => {
    await Promise.all([owner.dispose(), member.dispose(), outsider.dispose()])
  })

  // 【何のため】コミュニティを作ると、作った人がオーナーになることを確かめる
  test('作成すると、作った人がオーナーになる', async () => {
    const created = await json<{ id: number; myRole: string; memberCount: number; owner: { userId: number } }>(
      await owner.post('/api/communities', { name, description: 'API の E2E テスト' }),
      201,
    )
    communityId = created.id
    expect(created.myRole).toBe('owner')
    expect(created.memberCount).toBe(1)
    expect(created.owner.userId).toBe(account('owner').id)
  })

  // 【何のため】作ったコミュニティが、ログインしていない人の検索でも見つかることを確かめる
  test('検索で見つかる', async ({ request }) => {
    const result = await json<Page<{ id: number }>>(
      await request.get(`/api/communities?q=${encodeURIComponent(runId())}`),
    )
    expect(result.items.map((c) => c.id)).toContain(communityId)
  })

  // 【何のため】メンバーでない人は、編集やメンバーの一覧などの、管理の操作ができないことを確かめる
  test('メンバーでない人は、管理の操作ができない（403）', async () => {
    expect((await outsider.patch(`/api/communities/${communityId}`, { name: 'x' })).status()).toBe(403)
    expect((await outsider.get(`/api/communities/${communityId}/members`)).status()).toBe(403)
    expect((await outsider.get(`/api/communities/${communityId}/join-requests`)).status()).toBe(403)
  })

  // 【何のため】参加の申請 → オーナーが承認 → メンバーになる、の流れを確かめる
  test('参加の申請を承認すると、メンバーになる', async () => {
    const request = await json<JoinRequest>(await member.post(`/api/communities/${communityId}/join-requests`), 201)
    expect(request.status).toBe('pending')
    // 同じ人が、もう一度申請することはできない
    expect((await member.post(`/api/communities/${communityId}/join-requests`)).status()).toBe(409)

    const pending = await json<Page<JoinRequest>>(await owner.get(`/api/communities/${communityId}/join-requests`))
    expect(pending.items.map((r) => r.user.userId)).toContain(account('member').id)

    const decided = await json<JoinRequest>(
      await owner.patch(`/api/communities/${communityId}/join-requests/${request.id}`, { status: 'approved' }),
    )
    expect(decided.status).toBe('approved')

    const detail = await json<{ myRole: string; memberCount: number }>(
      await member.get(`/api/communities/${communityId}`),
    )
    expect(detail.myRole).toBe('member')
    expect(detail.memberCount).toBe(2)
  })

  // 【何のため】参加の申請を却下すると、メンバーにならないことを確かめる
  test('参加の申請を却下すると、メンバーにならない', async () => {
    const request = await json<JoinRequest>(await outsider.post(`/api/communities/${communityId}/join-requests`), 201)
    await json(await owner.patch(`/api/communities/${communityId}/join-requests/${request.id}`, { status: 'rejected' }))
    // 一度決めた申請は、もう変えられない
    expect(
      (await owner.patch(`/api/communities/${communityId}/join-requests/${request.id}`, { status: 'approved' })).status(),
    ).toBe(409)
    const detail = await json<{ myRole: string | null; myJoinRequestStatus: string }>(
      await outsider.get(`/api/communities/${communityId}`),
    )
    expect(detail.myRole).toBeNull()
    expect(detail.myJoinRequestStatus).toBe('rejected')
  })

  // 【何のため】ふつうのメンバーは、管理の操作ができないことを確かめる
  test('ふつうのメンバーは、管理の操作ができない（403）', async () => {
    expect((await member.get(`/api/communities/${communityId}/members`)).status()).toBe(403)
    expect((await member.post(`/api/communities/${communityId}/events`, { title: 'x' })).status()).toBe(403)
  })

  // 【何のため】運営スタッフに任命すると、管理の操作の一部ができるようになり、オーナーだけの操作はできないことを確かめる
  test('運営スタッフに任命・メンバーに戻す', async () => {
    const memberId = account('member').id
    await json(await owner.patch(`/api/communities/${communityId}/members/${memberId}`, { role: 'staff' }))

    // 運営スタッフは、メンバーの一覧を見られる。管理する人（オーナーと運営スタッフ）だけの一覧にも入る
    const managers = await json<Page<{ user: { userId: number }; role: string }>>(
      await member.get(`/api/communities/${communityId}/members?group=managers`),
    )
    expect(managers.items.map((m) => m.role).sort()).toEqual(['owner', 'staff'])
    // オーナーだけの操作（名前の変更、立場の変更）はできない
    expect((await member.patch(`/api/communities/${communityId}`, { name: 'x', description: '' })).status()).toBe(403)
    expect(
      (await member.patch(`/api/communities/${communityId}/members/${account('owner').id}`, { role: 'member' })).status(),
    ).toBe(403)

    await json(await owner.patch(`/api/communities/${communityId}/members/${memberId}`, { role: 'member' }))
    expect((await member.get(`/api/communities/${communityId}/members`)).status()).toBe(403)
  })

  // 【何のため】オーナーは、名前と説明を変えられることを確かめる
  test('オーナーは名前と説明を変えられる', async () => {
    const updated = await json<{ name: string; description: string }>(
      await owner.patch(`/api/communities/${communityId}`, { name: `${name}（編集）`, description: '編集しました' }),
    )
    expect(updated.name).toBe(`${name}（編集）`)
    expect(updated.description).toBe('編集しました')
  })

  // 【何のため】メンバーが自分で退会でき、オーナーは退会できないことを確かめる
  test('メンバーは退会できる。オーナーは退会できない', async () => {
    expect((await member.delete(`/api/communities/${communityId}/membership`)).status()).toBe(204)
    expect((await owner.delete(`/api/communities/${communityId}/membership`)).status()).toBe(409)
    const detail = await json<{ myRole: string | null }>(await member.get(`/api/communities/${communityId}`))
    expect(detail.myRole).toBeNull()
  })

  // 【何のため】オーナーが、メンバーを除名できることを確かめる（もう一度参加させてから除名する）
  test('オーナーはメンバーを除名できる', async () => {
    const request = await json<JoinRequest>(await member.post(`/api/communities/${communityId}/join-requests`), 201)
    await json(await owner.patch(`/api/communities/${communityId}/join-requests/${request.id}`, { status: 'approved' }))
    expect((await owner.delete(`/api/communities/${communityId}/members/${account('member').id}`)).status()).toBe(204)
    // オーナーは除名できない
    expect((await owner.delete(`/api/communities/${communityId}/members/${account('owner').id}`)).status()).toBe(409)
  })

  // 【何のため】オーナーをしているコミュニティが残っていると、退会できないことを確かめる
  test('オーナーをしているコミュニティがあると、退会できない（409）', async () => {
    const response = await owner.delete('/api/me', { password: account('owner').password })
    expect(response.status()).toBe(409)
    expect((await response.json()).code).toBe('OWNS_ACTIVE_COMMUNITIES')
  })

  // 【何のため】アーカイブすると検索に出なくなり、元に戻すと出ることを確かめる
  test('アーカイブと、元に戻す', async ({ request }) => {
    const search = async () =>
      (await json<Page<{ id: number }>>(await request.get(`/api/communities?q=${encodeURIComponent(runId())}`))).items.map(
        (c) => c.id,
      )

    const archived = await json<{ archivedAt: string | null }>(await owner.put(`/api/communities/${communityId}/archive`))
    expect(archived.archivedAt).not.toBeNull()
    expect(await search()).not.toContain(communityId)
    // アーカイブ中は、参加の申請を受け付けない
    expect((await outsider.post(`/api/communities/${communityId}/join-requests`)).status()).toBe(409)

    const restored = await json<{ archivedAt: string | null }>(await owner.delete(`/api/communities/${communityId}/archive`))
    expect(restored.archivedAt).toBeNull()
    expect(await search()).toContain(communityId)
  })
})

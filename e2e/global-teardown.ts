/**
 * 【役割】E2E テストの最後に、1回だけ行う後片付け。テストで作ったデータと利用者を消す。
 * 【なぜ必要か】本番でテストしたときに、テストのデータ（コミュニティ、イベント、利用者）を残さないため。
 *
 * このアプリの決まりで、退会するには「オーナーをしているコミュニティを、すべてアーカイブ」し、
 * 「そのコミュニティに、終わっていないイベントがない」必要がある（AccountService.delete）。そのため、次の順に片付ける。
 * ① 利用者ごとに、オーナーをしているコミュニティの、開催前のイベントを削除する
 * ② そのコミュニティをアーカイブする（アーカイブしたコミュニティは、一覧や検索に出ない）
 * ③ 退会する（名前とメールアドレスが消え、「退会したユーザー」になる）
 *
 * テストが途中で失敗しても片付けるため、1つ失敗しても、残りを続ける。
 */
import { ApiSession, json } from './support/api'
import { loadAccounts, type Account } from './support/accounts'

interface ManagedCommunity {
  id: number
  myRole: string
}
interface Page<T> {
  items: T[]
  totalPages: number
}

export default async function globalTeardown() {
  for (const acc of loadAccounts()) {
    try {
      await cleanUp(acc)
    } catch (e) {
      // 片付けに失敗しても、ほかの利用者の片付けは続ける。失敗した内容は表示する
      console.error(`E2E の後片付けに失敗しました（${acc.email}）：`, e)
    }
  }
}

/** 【何のため】1人の利用者の、データと、アカウントを片付ける */
async function cleanUp(acc: Account) {
  const session = await ApiSession.create()
  try {
    const login = await session.post('/api/auth/login', { email: acc.email, password: acc.password })
    if (login.status() === 401) {
      // 画面のテストの中で、すでに退会した利用者
      return
    }
    await json(login)

    // ① ② オーナーをしているコミュニティの、開催前のイベントを消して、アーカイブする
    for (const community of await managedCommunities(session)) {
      if (community.myRole !== 'owner') continue
      const events = await json<Page<{ id: number }>>(
        await session.get(`/api/communities/${community.id}/events?status=upcoming`),
      )
      for (const event of events.items) {
        await session.delete(`/api/communities/${community.id}/events/${event.id}`)
      }
      await session.put(`/api/communities/${community.id}/archive`)
    }

    // ③ 退会する
    const deleted = await session.delete('/api/me', { password: acc.password })
    if (deleted.status() !== 204) {
      throw new Error(`退会できませんでした（${deleted.status()}）：${await deleted.text()}`)
    }
  } finally {
    await session.dispose()
  }
}

/** 【何のため】管理しているコミュニティを、すべてのページから集める */
async function managedCommunities(session: ApiSession): Promise<ManagedCommunity[]> {
  const all: ManagedCommunity[] = []
  for (let page = 1; ; page++) {
    const result = await json<Page<ManagedCommunity>>(await session.get(`/api/me/managed-communities?page=${page}`))
    all.push(...result.items)
    if (page >= result.totalPages) return all
  }
}

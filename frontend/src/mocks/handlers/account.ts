/**
 * 【役割】自分のアカウントの偽の API（docs/api/openapi.yaml の account の /api/me）。
 * 【なぜ必要か】バックエンドができるまでの間、アカウント設定の画面（S-12）の動きを確かめるため。
 */

import { delay, http, HttpResponse } from 'msw'
import type { DeleteMeRequest, UpdateMeRequest } from '../../types/account'
import { db } from '../db'
import type { MockUser } from '../db/users'
import { checkCsrf, clearSessionCookie, findLoggedInUser, problem, toMe } from './common'
import { Validator } from './validation'

/**
 * 【何のため】ログインしていなければ 401 の返事を作る。ログインしていれば、そのユーザーを返す。
 * 呼び出す側は「返ってきたのが返事（Response）なら、それをそのまま返す」と書く。
 */
function requireLogin(cookies: Record<string, string>): MockUser | Response {
  const user = findLoggedInUser(cookies)
  return user ?? problem(401, 'UNAUTHENTICATED', 'ログインしてください。')
}

export const accountHandlers = [
  /**
   * 自分の情報を取得する（GET /api/me）。ログインしていなければ 401。
   */
  http.get('/api/me', async ({ cookies }) => {
    await delay(200)
    const user = requireLogin(cookies)
    // instanceof Response は「エラーの返事かどうか」を確かめる書き方
    if (user instanceof Response) {
      return user
    }
    return HttpResponse.json(toMe(user))
  }),

  /**
   * ニックネームを変更する（PATCH /api/me）。
   */
  http.patch('/api/me', async ({ request, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }

    const body = (await request.json()) as Partial<UpdateMeRequest>
    const validator = new Validator()
    const nickname = validator.text('nickname', body.nickname, 'ニックネーム', 1, 30)
    if (validator.hasErrors()) {
      return problem(400, 'VALIDATION_ERROR', '入力内容に誤りがあります。', { errors: validator.errors })
    }

    // ニックネームを書き換える。ほかの表はユーザーの番号でつながっているので、
    // 過去の申込みの記録などにも、自動で新しいニックネームが表示される（要件定義書 5.6）
    user.nickname = nickname
    return HttpResponse.json(toMe(user))
  }),

  /**
   * アカウントを削除する（DELETE /api/me）。要件定義書 5.5 のとおりに処理する。
   */
  http.delete('/api/me', async ({ request, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }

    // 本人の確認のため、パスワードを確かめる
    const body = (await request.json()) as Partial<DeleteMeRequest>
    const password = typeof body.password === 'string' ? body.password : ''
    if (password === '') {
      return problem(400, 'VALIDATION_ERROR', '入力内容に誤りがあります。', {
        errors: [{ field: 'password', message: 'パスワードを入力してください。' }],
      })
    }
    if (password !== user.password) {
      return problem(400, 'INVALID_PASSWORD', 'パスワードが正しくありません。', {
        errors: [{ field: 'password', message: 'パスワードが正しくありません。' }],
      })
    }

    // オーナーをしているコミュニティの確認（要件定義書 5.5）
    const ownedCommunities = db.communities.filter((c) => c.ownerId === user.id)
    // ① アーカイブしていないコミュニティがあれば、削除できない
    if (ownedCommunities.some((c) => c.archivedAt === null)) {
      return problem(409, 'OWNS_ACTIVE_COMMUNITIES', 'オーナーをしているコミュニティがあります。先にコミュニティをアーカイブしてください。')
    }
    // ② アーカイブ済みでも、開催前のイベントが残っていれば、削除できない
    const now = new Date()
    const ownedIds = ownedCommunities.map((c) => c.id)
    const hasUpcomingEvents = db.events.some((e) => ownedIds.includes(e.communityId) && new Date(e.endAt) > now)
    if (hasUpcomingEvents) {
      return problem(409, 'OWNED_COMMUNITY_HAS_UPCOMING_EVENTS', 'オーナーをしているコミュニティに、開催前のイベントが残っています。イベントが終わってから削除してください。')
    }

    // 参加しているすべてのコミュニティから退会する（オーナーの行は残す。アーカイブ済みのコミュニティのオーナーとして表示するため）
    db.memberships = db.memberships.filter((m) => m.userId !== user.id || m.role === 'owner')

    // 開催前のイベントへの申込みを、すべて取り消す。
    // キャンセル待ちの繰り上げとお知らせは、イベントの画面を作るとき（Issue #23）に、共通の取り消しの処理に置き換える
    const upcomingEventIds = db.events.filter((e) => new Date(e.endAt) > now).map((e) => e.id)
    db.applications = db.applications.filter((a) => a.userId !== user.id || !upcomingEventIds.includes(a.eventId))

    // 個人情報を消し、行だけを残す（過去の申込みの記録に「退会したユーザー」と表示するため）
    user.realName = ''
    user.nickname = ''
    user.email = ''
    user.password = ''
    user.deletedAt = now.toISOString()

    // ログアウトした状態にする
    return new HttpResponse(null, {
      status: 204,
      headers: { 'Set-Cookie': clearSessionCookie() },
    })
  }),
]

/**
 * 【役割】お知らせの偽の API（docs/api/openapi.yaml の notifications）。
 * 【なぜ必要か】バックエンドができるまでの間、お知らせの画面と、画面上部の未読の件数を確かめるため。
 * 既読かどうかは、お知らせの readAt（既読にした日時）の1つの項目で管理する（null なら未読）。
 * どの API も、ログインしている人の、自分あてのお知らせだけを扱う。
 */

import { delay, http, HttpResponse } from 'msw'
import type { Notification } from '../../types/notification'
import { db } from '../db'
import type { MockNotification } from '../db/notifications'
import { checkCsrf, paginate, parsePage, problem, requireLogin, toUserRef } from './common'

/**
 * 【何のため】偽のデータベースのお知らせを、画面に返す形にする。
 */
function toNotification(n: MockNotification): Notification {
  return {
    id: n.id,
    type: n.type,
    eventTitle: n.eventTitle,
    communityId: n.communityId,
    eventId: n.eventId,
    actor: n.actorUserId === null ? null : toUserRef(n.actorUserId),
    // readAt に日時が入っていれば既読
    read: n.readAt !== null,
    createdAt: n.createdAt,
  }
}

/**
 * 【何のため】あるユーザーあての、未読のお知らせの件数を数える。
 */
function countUnread(userId: number): number {
  return db.notifications.filter((n) => n.userId === userId && n.readAt === null).length
}

export const notificationHandlers = [
  /**
   * お知らせの一覧（GET /api/notifications）。自分あてのものを新しい順に返す。未読の件数も返す。
   */
  http.get('/api/notifications', async ({ request, cookies }) => {
    await delay(300)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const items = db.notifications
      .filter((n) => n.userId === user.id)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id)
      .map(toNotification)
    return HttpResponse.json({ ...paginate(items, parsePage(request)), unreadCount: countUnread(user.id) })
  }),

  /**
   * 未読のお知らせの件数（GET /api/notifications/unread-count）。画面上部の表示に使う。
   */
  http.get('/api/notifications/unread-count', async ({ cookies }) => {
    await delay(100)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    return HttpResponse.json({ count: countUnread(user.id) })
  }),

  /**
   * お知らせを1件、既読にする（PUT /api/notifications/:notificationId/read）。何度呼んでも結果は同じ。
   * 他人あてのお知らせは、存在しない場合と同じく 404 にする（他人あてのお知らせがあることを知られないため）。
   */
  http.put('/api/notifications/:notificationId/read', async ({ request, params, cookies }) => {
    await delay(200)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const notification = db.notifications.find((n) => n.id === Number(params.notificationId) && n.userId === user.id)
    if (!notification) {
      return problem(404, 'NOT_FOUND', 'お知らせが見つかりません。')
    }
    // ?? = は「左が空（null）のときだけ、右の値を入れる」という書き方。すでに既読なら日時を変えない
    notification.readAt ??= new Date().toISOString()
    return HttpResponse.json(toNotification(notification))
  }),

  /**
   * お知らせをすべて既読にする（POST /api/notifications/read-all）。
   */
  http.post('/api/notifications/read-all', async ({ request, cookies }) => {
    await delay(200)
    const user = requireLogin(cookies)
    if (user instanceof Response) {
      return user
    }
    const csrfError = checkCsrf(request, cookies)
    if (csrfError) {
      return csrfError
    }
    const now = new Date().toISOString()
    for (const n of db.notifications) {
      if (n.userId === user.id && n.readAt === null) {
        n.readAt = now
      }
    }
    return new HttpResponse(null, { status: 204 })
  }),
]

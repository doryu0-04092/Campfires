/**
 * 【役割】お知らせに関する API を呼び出す関数をまとめたファイル。
 * 【なぜ必要か】API の URL や呼び出し方を画面のコードに直接書かず、ここに集めておくため。
 * URL と返事の形は、API 仕様書（docs/api/openapi.yaml）の notifications に合わせている。
 */

import type { Notification, NotificationListResponse } from '../types/notification'
import { apiRequest } from './client'

/** 【何のため】お知らせの一覧を取得する（GET /api/notifications）。 */
export function fetchNotifications(page: number, signal?: AbortSignal) {
  const query = page > 1 ? `?page=${page}` : ''
  return apiRequest<NotificationListResponse>(`/api/notifications${query}`, { signal })
}

/** 【何のため】未読のお知らせの件数を取得する（GET /api/notifications/unread-count）。 */
export async function fetchUnreadCount(signal?: AbortSignal): Promise<number> {
  const result = await apiRequest<{ count: number }>('/api/notifications/unread-count', { signal })
  return result.count
}

/** 【何のため】お知らせを1件、既読にする（PUT /api/notifications/:notificationId/read）。 */
export function markAsRead(notificationId: number) {
  return apiRequest<Notification>(`/api/notifications/${notificationId}/read`, { method: 'PUT' })
}

/** 【何のため】お知らせをすべて既読にする（POST /api/notifications/read-all）。 */
export function markAllAsRead() {
  return apiRequest<void>('/api/notifications/read-all', { method: 'POST' })
}

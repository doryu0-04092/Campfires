/**
 * 【役割】イベントに関する API を呼び出す関数をまとめたファイル。
 * 【なぜ必要か】API の URL や呼び出し方を画面のコードに直接書かず、ここに集めておくため。
 * バックエンドの API が変わったときは、このファイルだけを直せばよい。
 * 今は偽の API（MSW）が返事をし、バックエンドができたら本物の API が返事をする。どちらでもこのファイルは同じ。
 */

import type {
  ApplicationListResponse,
  CommunityEventListResponse,
  EventDetail,
  EventInput,
  EventListResponse,
  MyApplication,
} from '../types/event'
import { apiRequest } from './client'

/**
 * 【何のため】fetchEvents に渡す値の形を決める。
 */
type FetchEventsParams = {
  // 検索欄に入力された文字（空なら、検索していない）
  query: string
  // 表示したいページ番号
  page: number
  // 通信を途中で取りやめるための合図（AbortSignal）。
  // 検索を続けて何度も行ったとき、古い通信の結果で画面が上書きされないようにするために使う
  signal?: AbortSignal
}

/**
 * 【何のため】イベント一覧・検索の API（GET /api/events）を呼び出し、結果を返す。
 * 失敗したときは、共通の処理（apiRequest）が ApiError を投げる。
 */
export async function fetchEvents({ query, page, signal }: FetchEventsParams): Promise<EventListResponse> {
  // URL の「?」より後ろの部分（例：q=Java&page=2）を組み立てる。
  // URLSearchParams を使うと、日本語や空白を URL で使える形に自動で変換してくれる
  const params = new URLSearchParams()
  if (query !== '') {
    params.set('q', query)
  }
  if (page > 1) {
    params.set('page', String(page))
  }

  return apiRequest<EventListResponse>(`/api/events?${params.toString()}`, { signal })
}

/**
 * 【何のため】イベントの URL の共通部分（/api/communities/:communityId/events/:eventId）を作る。
 */
function eventPath(communityId: number, eventId: number): string {
  return `/api/communities/${communityId}/events/${eventId}`
}

/** 【何のため】コミュニティのイベントの一覧を取得する（GET /api/communities/:communityId/events）。 */
export function fetchCommunityEvents(communityId: number, status: 'upcoming' | 'past', page: number, signal?: AbortSignal) {
  const params = new URLSearchParams({ status })
  if (page > 1) {
    params.set('page', String(page))
  }
  return apiRequest<CommunityEventListResponse>(`/api/communities/${communityId}/events?${params.toString()}`, { signal })
}

/** 【何のため】イベントの詳細を取得する（GET /api/communities/:communityId/events/:eventId）。 */
export function fetchEvent(communityId: number, eventId: number, signal?: AbortSignal) {
  return apiRequest<EventDetail>(eventPath(communityId, eventId), { signal })
}

/** 【何のため】イベントを作成する（POST /api/communities/:communityId/events）。 */
export function createEvent(communityId: number, input: EventInput) {
  return apiRequest<EventDetail>(`/api/communities/${communityId}/events`, { method: 'POST', body: input })
}

/** 【何のため】イベントを編集する（PATCH /api/communities/:communityId/events/:eventId）。 */
export function updateEvent(communityId: number, eventId: number, input: EventInput) {
  return apiRequest<EventDetail>(eventPath(communityId, eventId), { method: 'PATCH', body: input })
}

/** 【何のため】イベントを削除する（DELETE /api/communities/:communityId/events/:eventId）。 */
export function deleteEvent(communityId: number, eventId: number) {
  return apiRequest<void>(eventPath(communityId, eventId), { method: 'DELETE' })
}

/** 【何のため】イベントに申し込む（POST /api/communities/:communityId/events/:eventId/applications）。 */
export function applyToEvent(communityId: number, eventId: number) {
  return apiRequest<MyApplication>(`${eventPath(communityId, eventId)}/applications`, { method: 'POST' })
}

/** 【何のため】自分の申込みを取り消す（DELETE /api/communities/:communityId/events/:eventId/my-application）。 */
export function cancelMyApplication(communityId: number, eventId: number) {
  return apiRequest<void>(`${eventPath(communityId, eventId)}/my-application`, { method: 'DELETE' })
}

/** 【何のため】申込者の一覧を取得する（GET /api/communities/:communityId/events/:eventId/applications）。 */
export function fetchApplications(communityId: number, eventId: number, page: number, signal?: AbortSignal) {
  const query = page > 1 ? `?page=${page}` : ''
  return apiRequest<ApplicationListResponse>(`${eventPath(communityId, eventId)}/applications${query}`, { signal })
}

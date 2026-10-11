/**
 * 【役割】ログインとアカウントに関する API を呼び出す関数をまとめたファイル。
 * 【なぜ必要か】API の URL や呼び出し方を画面のコードに直接書かず、ここに集めておくため。
 * URL と返事の形は、API 仕様書（docs/api/openapi.yaml）の auth と account に合わせている。
 */

import type { LoginRequest, Me } from '../types/account'
import { ApiError, apiRequest } from './client'

/**
 * 【何のため】CSRF トークンを受け取る（GET /api/auth/csrf）。
 * サーバーが XSRF-TOKEN の Cookie を渡してくれるので、画面を開いたときに最初に1回呼ぶ。
 */
export async function fetchCsrfToken(): Promise<void> {
  await apiRequest<void>('/api/auth/csrf')
}

/**
 * 【何のため】ログインしている自分の情報を取得する（GET /api/me）。
 * ログインしていないとき（401）は、エラーにせず null を返す。
 * 「ログインしていない」は異常ではなく、よくある状態なので、呼び出す側で扱いやすくするため。
 */
export async function fetchMe(): Promise<Me | null> {
  try {
    return await apiRequest<Me>('/api/me')
  } catch (error) {
    // instanceof は「この種類のエラーか」を確かめる書き方
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    // それ以外のエラーは、呼び出す側に知らせる
    throw error
  }
}

/**
 * 【何のため】ログインする（POST /api/auth/login）。成功したら、自分の情報を返す。
 * 失敗したときは ApiError を投げる（code は INVALID_CREDENTIALS、ACCOUNT_LOCKED など）。
 */
export async function login(request: LoginRequest): Promise<Me> {
  return apiRequest<Me>('/api/auth/login', { method: 'POST', body: request })
}

/**
 * 【何のため】ログアウトする（POST /api/auth/logout）。
 */
export async function logout(): Promise<void> {
  await apiRequest<void>('/api/auth/logout', { method: 'POST' })
}

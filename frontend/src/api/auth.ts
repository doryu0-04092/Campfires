/**
 * 【役割】ログインとアカウントに関する API を呼び出す関数をまとめたファイル。
 * 【なぜ必要か】API の URL や呼び出し方を画面のコードに直接書かず、ここに集めておくため。
 * URL と返事の形は、API 仕様書（docs/api/openapi.yaml）の auth と account に合わせている。
 */

import type {
  AuthFeatures,
  ChangePasswordRequest,
  DeleteMeRequest,
  LoginRequest,
  Me,
  PasswordResetConfirmRequest,
  PasswordResetRequest,
  SignupRequest,
  UpdateMeRequest,
} from '../types/account'
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

/**
 * 【何のため】アカウントを登録する（POST /api/auth/signup）。成功したら、そのままログインした状態になり、自分の情報を返す。
 * 失敗したときは ApiError を投げる（code は VALIDATION_ERROR、EMAIL_ALREADY_REGISTERED）。
 */
export async function signup(request: SignupRequest): Promise<Me> {
  return apiRequest<Me>('/api/auth/signup', { method: 'POST', body: request })
}

/**
 * 【何のため】ニックネームを変更する（PATCH /api/me）。成功したら、変更後の自分の情報を返す。
 */
export async function updateMe(request: UpdateMeRequest): Promise<Me> {
  return apiRequest<Me>('/api/me', { method: 'PATCH', body: request })
}

/**
 * 【何のため】アカウントを削除する（DELETE /api/me）。
 * 失敗したときは ApiError を投げる（code は INVALID_PASSWORD、OWNS_ACTIVE_COMMUNITIES など）。
 */
export async function deleteMe(request: DeleteMeRequest): Promise<void> {
  await apiRequest<void>('/api/me', { method: 'DELETE', body: request })
}

/**
 * 【何のため】パスワードを変更する（PUT /api/me/password）。
 * 成功すると、サーバーがセッションを作り直す（ほかの端末のログインは使えなくなり、この端末はログインしたまま）。
 * 失敗したときは ApiError を投げる（code は INVALID_PASSWORD、VALIDATION_ERROR など）。
 */
export async function changePassword(request: ChangePasswordRequest): Promise<void> {
  await apiRequest<void>('/api/me/password', { method: 'PUT', body: request })
}

/**
 * 【何のため】今使える機能の一覧を取得する（GET /api/auth/features）。ログインしていなくても呼べる。
 * 「パスワードを忘れた場合」のリンクを出すかどうかを決めるために使う。
 */
export async function fetchAuthFeatures(signal?: AbortSignal): Promise<AuthFeatures> {
  return apiRequest<AuthFeatures>('/api/auth/features', { signal })
}

/**
 * 【何のため】パスワードの再設定を依頼する（POST /api/auth/password-reset-requests）。
 * 登録されていても、いなくても、同じ返事（202）になる（登録されているメールアドレスを探られないように）。
 * 再設定を止めているときは ApiError（503、PASSWORD_RESET_UNAVAILABLE）を投げる。
 */
export async function requestPasswordReset(request: PasswordResetRequest): Promise<void> {
  await apiRequest<{ message: string }>('/api/auth/password-reset-requests', { method: 'POST', body: request })
}

/**
 * 【何のため】リンクのトークンと新しいパスワードで、パスワードを決め直す（POST /api/auth/password-resets）。
 * 失敗したときは ApiError を投げる（code は INVALID_RESET_TOKEN、VALIDATION_ERROR、PASSWORD_RESET_UNAVAILABLE）。
 */
export async function resetPassword(request: PasswordResetConfirmRequest): Promise<void> {
  await apiRequest<void>('/api/auth/password-resets', { method: 'POST', body: request })
}

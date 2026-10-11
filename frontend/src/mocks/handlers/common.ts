/**
 * 【役割】偽の API で共通に使う処理をまとめたファイル。
 * 【なぜ必要か】エラーの返し方、CSRF トークンの確認、ログインしているかの確認は、
 * 多くの API で同じことをするため、1か所にまとめる。
 * 本物のバックエンドでは、Spring Security と、例外をまとめて処理する仕組みがこの役割を担う。
 */

import { HttpResponse } from 'msw'
import type { FieldError, PageInfo, Problem } from '../../types/api'
import type { Me } from '../../types/account'
import type { UserRef } from '../../types/user'
import { db } from '../db'
import type { MockRole } from '../db/communities'
import type { MockUser } from '../db/users'

// 一覧の1ページの件数（docs/api/README.md 1.8）
export const PAGE_SIZE = 30

// ログインの証明に使う Cookie の名前。
// 本物のバックエンドでは JSESSIONID（docs/api/openapi.yaml の sessionCookie）を使う
export const SESSION_COOKIE = 'mockSession'
// CSRF トークンに使う Cookie の名前（docs/api/README.md 1.7）
export const CSRF_COOKIE = 'XSRF-TOKEN'

// HTTP ステータスコードの短い説明（Problem の title に入れる）
const STATUS_TITLES: Record<number, string> = {
  400: 'Bad Request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not Found',
  409: 'Conflict',
  429: 'Too Many Requests',
}

/**
 * 【何のため】エラーの返事（Problem Details）を作る（docs/api/README.md 1.5）。
 */
export function problem(
  status: number,
  code: string,
  detail: string,
  options: { errors?: FieldError[]; headers?: Record<string, string> } = {},
) {
  const body: Problem = {
    type: 'about:blank',
    title: STATUS_TITLES[status] ?? 'Error',
    status,
    detail,
    code,
    // errors があるときだけ項目を入れる。... の後ろが空のオブジェクトなら、何も足されない
    ...(options.errors ? { errors: options.errors } : {}),
  }
  return HttpResponse.json(body, {
    status,
    headers: { 'Content-Type': 'application/problem+json', ...options.headers },
  })
}

/**
 * 【何のため】CSRF トークンが正しいかを確かめる（docs/api/README.md 1.7）。
 * Cookie の XSRF-TOKEN と、ヘッダーの X-XSRF-TOKEN が一致すれば正しい。
 * 正しくなければ 403 の返事を、正しければ null を返す。呼び出す側は「null でなければ、それを返す」と書く。
 */
export function checkCsrf(request: Request, cookies: Record<string, string>) {
  const cookieToken = cookies[CSRF_COOKIE]
  const headerToken = request.headers.get('X-XSRF-TOKEN')
  if (!cookieToken || cookieToken !== headerToken) {
    return problem(403, 'CSRF_TOKEN_INVALID', '画面を読み込み直してから、もう一度お試しください。')
  }
  return null
}

/**
 * 【何のため】ログインの証明の Cookie から、ログインしているユーザーを探す。
 * ログインしていなければ null を返す。
 *
 * 偽物なので、Cookie の値は「user-ユーザーの番号」という推測できる形にしている。
 * こうすると、ページを読み込み直してもログインが続く。
 * 本物のバックエンドでは、推測できないランダムな値を使い、サーバー側でユーザーと結び付けて覚えておく。
 */
export function findLoggedInUser(cookies: Record<string, string>): MockUser | null {
  const value = cookies[SESSION_COOKIE]
  if (!value?.startsWith('user-')) {
    return null
  }
  const userId = Number(value.slice('user-'.length))
  // アカウントを削除したユーザーは、ログインしていないものとして扱う
  return db.users.find((user) => user.id === userId && user.deletedAt === null) ?? null
}

/**
 * 【何のため】ログインの証明の Cookie を渡すための、Set-Cookie ヘッダーの値を作る。
 * 偽の API はブラウザの中で動くプログラムなので、HttpOnly と Secure は付けられない（付けると Cookie が保存されない）。
 * 本物のバックエンドでは、HttpOnly、Secure、SameSite=Lax を付ける（docs/api/README.md 1.6）。
 */
export function sessionCookie(userId: number): string {
  return `${SESSION_COOKIE}=user-${userId}; Path=/; SameSite=Lax`
}

/**
 * 【何のため】ログインの証明の Cookie を消すための、Set-Cookie ヘッダーの値を作る。
 * Max-Age=0 は「今すぐ期限切れにする」という意味で、ブラウザは Cookie を消す。
 */
export function clearSessionCookie(): string {
  return `${SESSION_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`
}

/**
 * 【何のため】ログインしていなければ 401 の返事を作る。ログインしていれば、そのユーザーを返す。
 * 呼び出す側は「返ってきたのが返事（Response）なら、それをそのまま返す」と書く。
 */
export function requireLogin(cookies: Record<string, string>): MockUser | Response {
  return findLoggedInUser(cookies) ?? problem(401, 'UNAUTHENTICATED', 'ログインしてください。')
}

/**
 * 【何のため】コミュニティでのユーザーの立場を調べる。メンバーでなければ null。
 */
export function findRole(communityId: number, userId: number | undefined): MockRole | null {
  if (userId === undefined) {
    return null
  }
  return db.memberships.find((m) => m.communityId === communityId && m.userId === userId)?.role ?? null
}

/**
 * 【何のため】ほかのユーザーを表す形（UserRef）に変える。ニックネームだけを返し、本名とメールアドレスは返さない。
 * アカウントを削除した人は、ニックネームを null にする（画面で「退会したユーザー」と表示する）。
 */
export function toUserRef(userId: number): UserRef {
  const user = db.users.find((u) => u.id === userId)
  return { userId, nickname: user && user.deletedAt === null ? user.nickname : null }
}

/**
 * 【何のため】URL の page の値を、ページ番号として使える数に変える。数でない、または 1 より小さいときは 1。
 */
export function parsePage(request: Request): number {
  const page = Number(new URL(request.url).searchParams.get('page') ?? '1')
  return Number.isInteger(page) && page >= 1 ? page : 1
}

/**
 * 【何のため】一覧を、指定したページの分だけ切り出し、ページの情報と一緒に返す（docs/api/README.md 1.8）。
 * <T> は「一覧の中身の形は、呼び出す側が決める」という印。
 */
export function paginate<T>(rows: T[], page: number): PageInfo & { items: T[] } {
  const start = (page - 1) * PAGE_SIZE
  return {
    items: rows.slice(start, start + PAGE_SIZE),
    totalCount: rows.length,
    page,
    pageSize: PAGE_SIZE,
    totalPages: Math.max(1, Math.ceil(rows.length / PAGE_SIZE)),
  }
}

/**
 * 【何のため】ユーザーの情報を、本人に返す形（Me）に変える。パスワードは返さない。
 */
export function toMe(user: MockUser): Me {
  return {
    id: user.id,
    realName: user.realName,
    nickname: user.nickname,
    email: user.email,
    createdAt: user.createdAt,
  }
}

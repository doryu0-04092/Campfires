/**
 * 【役割】API を、外から HTTP で呼ぶための道具（画面を使わない E2E テストと、テストの準備・後片付けで使う）。
 * 【なぜ必要か】このアプリの API は、データを変えるときに CSRF トークン（XSRF-TOKEN の Cookie と、X-XSRF-TOKEN のヘッダー）が要る。
 * 毎回それを付ける手間を、ここにまとめるため。ログインの Cookie も、ここで覚えておく。
 */
import { request, type APIRequestContext, type APIResponse } from '@playwright/test'

import { BASE_URL } from './env'

/** 1人の利用者の、API とのやり取り（Cookie を覚えている） */
export class ApiSession {
  // private constructor は「new では作らず、下の create を使ってほしい」という書き方
  private constructor(readonly context: APIRequestContext) {}

  /**
   * 【何のため】API とのやり取りを始める。storageState を渡すと、保存しておいたログインの Cookie を使う。
   */
  static async create(storageState?: string): Promise<ApiSession> {
    const context = await request.newContext({ baseURL: BASE_URL, storageState })
    const session = new ApiSession(context)
    // 最初に CSRF トークンの Cookie を受け取る（画面を開いたときと同じ）
    await session.context.get('/api/auth/csrf')
    return session
  }

  /** 【何のため】Cookie に入っている CSRF トークンを読む */
  private async csrfToken(): Promise<string> {
    const cookies = await this.context.storageState()
    const token = cookies.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value
    if (!token) throw new Error('XSRF-TOKEN の Cookie がありません')
    return token
  }

  /** 【何のため】データを読む（GET）。CSRF トークンは要らない */
  get(path: string): Promise<APIResponse> {
    return this.context.get(path)
  }

  /** 【何のため】データを変える（POST・PATCH・PUT・DELETE）。CSRF トークンを付ける */
  async send(method: 'POST' | 'PATCH' | 'PUT' | 'DELETE', path: string, data?: unknown): Promise<APIResponse> {
    return this.context.fetch(path, {
      method,
      data,
      headers: { 'X-XSRF-TOKEN': await this.csrfToken() },
    })
  }

  post(path: string, data?: unknown) {
    return this.send('POST', path, data)
  }
  patch(path: string, data?: unknown) {
    return this.send('PATCH', path, data)
  }
  put(path: string, data?: unknown) {
    return this.send('PUT', path, data)
  }
  delete(path: string, data?: unknown) {
    return this.send('DELETE', path, data)
  }

  /** 【何のため】ログインの Cookie を、ファイルに保存する（画面のテストで、ログインした状態から始めるため） */
  async saveState(file: string): Promise<void> {
    await this.context.storageState({ path: file })
  }

  /** 【何のため】やり取りを終える */
  async dispose(): Promise<void> {
    await this.context.dispose()
  }
}

/**
 * 【何のため】返事の JSON を読む。思った番号（200 など）でなければ、中身を付けてエラーにする（原因を分かりやすくするため）。
 * <T> は「返す値の型を、呼ぶ側が決める」という書き方
 */
export async function json<T>(response: APIResponse, expectedStatus = 200): Promise<T> {
  if (response.status() !== expectedStatus) {
    throw new Error(`${response.url()} は ${expectedStatus} のはずが ${response.status()}：${await response.text()}`)
  }
  return (await response.json()) as T
}

/**
 * 【何のため】今から days 日後の日時を、API が受け取る形（ISO 8601、例：2026-10-20T10:00:00Z）で返す。
 * イベントの開始日時は「未来」でなければならないので、テストの日時は、いつも今から先にする。
 */
export function daysFromNow(days: number, hours = 0): string {
  const date = new Date(Date.now() + (days * 24 + hours) * 60 * 60 * 1000)
  // 秒より細かい部分は使わない
  date.setUTCSeconds(0, 0)
  return date.toISOString()
}

/** 【何のため】イベントを作るときの、ふつうの入力（必要な項目だけを上書きして使う） */
export function eventInput(overrides: Record<string, unknown> = {}) {
  return {
    title: 'E2E イベント',
    description: 'E2E テストで作ったイベントです。',
    startAt: daysFromNow(10),
    endAt: daysFromNow(10, 2),
    applicationDeadline: daysFromNow(9),
    location: 'オンライン',
    capacity: 5,
    memberApproval: 'auto',
    nonMemberApproval: 'auto',
    ...overrides,
  }
}

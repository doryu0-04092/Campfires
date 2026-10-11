/**
 * 【役割】すべての API の呼び出しで使う、共通の処理をまとめたファイル。
 * 【なぜ必要か】API を呼ぶときに毎回必要な作業（CSRF トークンを付ける、データを JSON にする、
 * エラーの返事を読み取る）を1か所にまとめ、API ごとに同じことを書かずに済むようにするため。
 * ルールは docs/api/README.md の 1章（共通ルール）に合わせている。
 */

import type { FieldError, Problem } from '../types/api'

/**
 * 【何のため】API がエラーを返したことを表す「エラーの種類」。
 * JavaScript に元からある Error を土台にして（extends）、API のエラーの情報を足している。
 * 画面は、このエラーの code を見て、何を表示するかを決める。
 */
export class ApiError extends Error {
  // HTTP ステータスコード（例：409）
  readonly status: number
  // エラーの種類を表す記号（例："EVENT_FULL"）
  readonly code: string
  // 入力のチェックで引っかかった項目の一覧（ないときは空の一覧）
  readonly fieldErrors: FieldError[]

  /**
   * 【何のため】API のエラーの返事から、ApiError を作る。
   * constructor は「new ApiError(...)」で作るときに呼ばれる関数。
   * readonly は「作ったあとは変えられない」という印。
   */
  constructor(problem: Problem) {
    // 土台の Error に、利用者に見せる説明（detail）を渡す
    super(problem.detail)
    this.name = 'ApiError'
    this.status = problem.status
    this.code = problem.code
    this.fieldErrors = problem.errors ?? []
  }
}

/**
 * 【何のため】Cookie の中から、指定した名前の値を取り出す。
 * CSRF トークン（XSRF-TOKEN）を読むために使う。
 * ログインの証明（セッション ID）は HttpOnly なので、この方法では読めない（読む必要もない）。
 */
function readCookie(name: string): string | null {
  // document.cookie は "名前1=値1; 名前2=値2" のような1つの文字になっている。
  // "; " で区切って1つずつ見ていき、名前が一致するものを探す
  for (const part of document.cookie.split('; ')) {
    const [key, ...rest] = part.split('=')
    if (key === name) {
      // 値の中に = が含まれていても壊れないように、残りをつなぎ直す。
      // decodeURIComponent は、URL で使える形に変換された文字を元に戻す関数
      return decodeURIComponent(rest.join('='))
    }
  }
  return null
}

// データを変える操作。この操作のときだけ CSRF トークンを付ける（docs/api/README.md 1.7）
const UNSAFE_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE']

/**
 * 【何のため】apiRequest に渡す値の形を決める。
 */
type RequestOptions = {
  // 操作の種類。省略したら GET（取得）
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  // 送るデータ。JSON に変換して送る
  body?: unknown
  // 通信を途中で取りやめるための合図
  signal?: AbortSignal
}

/**
 * 【何のため】API を呼び出し、成功したら返事のデータを、失敗したら ApiError を投げる。
 * <T> は「返事のデータの形を、呼び出す側が決める」という印（ジェネリクス）。
 * 例：apiRequest<Me>('/api/me') と書くと、返事を Me の形として受け取れる。
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'

  // 通信に付ける追加の情報（ヘッダー）
  const headers: Record<string, string> = {
    // 返事は JSON でほしい、と伝える
    Accept: 'application/json',
  }

  // 送るデータがあれば、「中身は JSON です」と伝える
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  // データを変える操作なら、Cookie の CSRF トークンをヘッダーに入れて送る
  if (UNSAFE_METHODS.includes(method)) {
    const token = readCookie('XSRF-TOKEN')
    if (token !== null) {
      headers['X-XSRF-TOKEN'] = token
    }
  }

  const response = await fetch(path, {
    method,
    headers,
    // JSON.stringify は、データを JSON の文字に変換する関数
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    // 同じ場所（同じオリジン）への通信では、Cookie を送る
    credentials: 'same-origin',
    signal: options.signal,
  })

  // 失敗したとき（200番台でないとき）は、エラーの返事を読み取って ApiError を投げる
  if (!response.ok) {
    throw new ApiError(await readProblem(response))
  }

  // 204（返す中身なし）のときは、読み取るものがないので何も返さない
  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

/**
 * 【何のため】エラーの返事から、Problem（エラーの情報）を読み取る。
 * サーバーが想定外のエラーで、決まった形の返事を返さなかった場合にも、画面が壊れないようにする。
 */
async function readProblem(response: Response): Promise<Problem> {
  try {
    return (await response.json()) as Problem
  } catch {
    // JSON として読めなかったときは、最低限の情報でエラーを作る
    return {
      type: 'about:blank',
      title: response.statusText,
      status: response.status,
      detail: '通信中にエラーが発生しました。時間をおいて、もう一度お試しください。',
      code: 'UNKNOWN_ERROR',
    }
  }
}

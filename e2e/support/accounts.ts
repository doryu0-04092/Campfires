/**
 * 【役割】E2E テストで使う利用者（テスト用のアカウント）を作る・覚える・片付けるための道具。
 * 【なぜ必要か】本番には、開発用のデータ（たろう など）がない。テストは、毎回自分で利用者を作り、終わったら消す。
 * 本番の回数制限（アカウントの登録は、同じ IP アドレスから 1時間に 10回まで）に収まるように、
 * 1回の実行で作る利用者は少なくし（準備で3人、画面のテストで1人）、API のテストと画面のテストで使い回す。
 *
 * 作った利用者は .auth/accounts.json に書き、最後（global-teardown）に、すべて片付ける（退会する）。
 */
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'

import { test } from '@playwright/test'

import { ApiSession, json } from './api'
import { AUTH_DIR, IS_HTTPS } from './env'

/** テスト用の利用者の役 */
export type Role = 'owner' | 'member' | 'outsider'

export interface Account {
  role: Role | 'extra'
  id: number
  email: string
  password: string
  nickname: string
  realName: string
}

const ACCOUNTS_FILE = path.join(AUTH_DIR, 'accounts.json')
const RUN_FILE = path.join(AUTH_DIR, 'run.json')

/** 【何のため】実行ごとの番号（データの名前に付けて、ほかの実行や本物のデータと区別する）を返す */
export function runId(): string {
  return JSON.parse(fs.readFileSync(RUN_FILE, 'utf8')).runId
}

/**
 * 【何のため】データの名前に付ける印（実行ごとの番号 ＋ プロジェクトの名前の先頭2文字。例：mf3k2a1b-fi）を返す。
 * 同じ画面のテストを、Edge・Firefox・WebKit などで続けて行うので、プロジェクトごとに別の名前のデータを作り、
 * 「同じ名前のリンクが2つある」とならないようにするため。
 * 名前には文字数の上限がある（コミュニティの名前は50文字まで）ので、プロジェクトの名前は先頭2文字だけにする
 * （browser → br、firefox → fi、webkit → we、mobile → mo、a11y → a1）。
 * テストの外（準備・後片付け）で呼んだときは、実行ごとの番号だけを返す。
 */
export function tag(): string {
  try {
    // test.info()：今動いているテストの情報。テストの外で呼ぶとエラーになるので、try … catch で受け止める
    // slice(0, 2)：文字列の先頭2文字を取り出す
    return `${runId()}-${test.info().project.name.slice(0, 2)}`
  } catch {
    return runId()
  }
}

/** 【何のため】実行ごとの番号を決めて保存する（準備のときに1回だけ） */
export function startRun(): string {
  fs.mkdirSync(AUTH_DIR, { recursive: true })
  // 日時を36進数（0-9 と a-z）にして、短くする
  const id = Date.now().toString(36)
  fs.writeFileSync(RUN_FILE, JSON.stringify({ runId: id }))
  fs.writeFileSync(ACCOUNTS_FILE, '[]')
  return id
}

/** 【何のため】作った利用者の一覧を読む */
export function loadAccounts(): Account[] {
  if (!fs.existsSync(ACCOUNTS_FILE)) return []
  return JSON.parse(fs.readFileSync(ACCOUNTS_FILE, 'utf8'))
}

/** 【何のため】役から、利用者を探す */
export function account(role: Role): Account {
  const found = loadAccounts().find((a) => a.role === role)
  if (!found) throw new Error(`${role} の利用者が、まだ作られていません（global-setup を確かめてください）`)
  return found
}

/** 【何のため】その役の、ログインの Cookie を保存したファイルの場所 */
export function storageStatePath(role: Role): string {
  return path.join(AUTH_DIR, `${role}.json`)
}

/**
 * 【何のため】その役の、画面のテスト（ブラウザ）で使う、ログインの Cookie のファイルの場所。
 * 手元の箱（http://localhost）では、Cookie の secure の印を外したものを使う（saveSession を参照）。
 */
export function browserStatePath(role: Role): string {
  return path.join(AUTH_DIR, `${role}.browser.json`)
}

/**
 * 【何のため】ログインした ApiSession の Cookie を、2つのファイルに保存する。
 * - storageStatePath：サーバーから受け取ったまま（API のテストで使う。Cookie の守りの確認にも使う）
 * - browserStatePath：画面のテストで使う。手元の箱（http）のときだけ、secure（HTTPS のときだけ送る、という印）を外す。
 *   ログインの Cookie（JSESSIONID）には secure が付いている。Edge・Firefox は localhost を安全な場所として送るが、
 *   WebKit（Safari の仕組み）は http では送らないため、WebKit の画面のテストが「ログインしていない」状態になってしまう。
 *   本番（https）では、何も変えない
 */
export async function saveSession(session: ApiSession, role: Role): Promise<void> {
  await session.saveState(storageStatePath(role))
  const state = JSON.parse(fs.readFileSync(storageStatePath(role), 'utf8'))
  if (!IS_HTTPS) {
    // for … of：配列の中身を1つずつ取り出して、同じ処理をする
    for (const cookie of state.cookies) cookie.secure = false
  }
  fs.writeFileSync(browserStatePath(role), JSON.stringify(state, null, 2))
}

/** 【何のため】作った利用者を、一覧に書き足す（あとで片付けるため。画面のテストで登録した利用者も、ここに書く） */
export function remember(acc: Account): void {
  const all = loadAccounts().filter((a) => a.email !== acc.email)
  all.push(acc)
  fs.writeFileSync(ACCOUNTS_FILE, JSON.stringify(all, null, 2))
}

/**
 * 【何のため】退会した利用者を、一覧から消す（後片付けで、もう一度ログインしようとして、ログインの回数を使わないため）
 */
export function forget(email: string): void {
  // filter：条件に合うものだけを残す（ここでは、メールアドレスが違うもの）
  fs.writeFileSync(ACCOUNTS_FILE, JSON.stringify(loadAccounts().filter((a) => a.email !== email), null, 2))
}

/** 【何のため】テスト用の利用者の、メールアドレスなどを決める（パスワードは、実行ごとにランダムに作る） */
export function newAccountData(role: Account['role'], label: string) {
  const id = runId()
  return {
    role,
    // example.com は、例として使うために決められたドメイン（本物のメールは届かない）
    email: `e2e-${id}-${label}@example.com`,
    // 32文字のランダムな文字列（randomBytes は暗号に使える乱数）
    password: crypto.randomBytes(24).toString('base64url'),
    nickname: `e2e-${label}`,
    realName: `E2E ${label}`,
  }
}

/** 【何のため】API で利用者を登録し、ログインした状態の ApiSession を返す */
export async function signupViaApi(role: Role): Promise<ApiSession> {
  const data = newAccountData(role, role)
  const session = await ApiSession.create()
  const me = await json<{ id: number }>(
    await session.post('/api/auth/signup', {
      realName: data.realName,
      nickname: data.nickname,
      email: data.email,
      password: data.password,
      agreedToPolicies: true,
    }),
    201,
  )
  remember({ ...data, id: me.id })
  await saveSession(session, role)
  return session
}

/** 【何のため】保存したログインの Cookie で、その役の ApiSession を作る */
export function sessionOf(role: Role): Promise<ApiSession> {
  return ApiSession.create(storageStatePath(role))
}

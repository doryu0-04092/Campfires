/**
 * 【役割】E2E テストで使う、テストする場所（URL）と、実行ごとの設定をまとめたファイル。
 * 【なぜ必要か】同じテストを、手元の箱（http://localhost:8081）にも、本番（AWS の URL）にも、URL を変えるだけで使えるようにするため。
 *
 * 使い方：E2E_BASE_URL=https://〜 npm test（指定しなければ、手元の本番用の箱）
 */
import path from 'node:path'
import { fileURLToPath } from 'node:url'

/** テストする場所の URL（最後の / は付けない） */
export const BASE_URL = (process.env.E2E_BASE_URL ?? 'http://localhost:8081').replace(/\/$/, '')

/**
 * 【何のため】手元の箱をテストしているかを返す。
 * 手元だけで行うテスト（ログインのロック、回数制限）は、本番では行わない。本番で行うと、本物の利用者に影響するため
 * （同じ IP アドレスからのログインや登録が、しばらくできなくなる）。
 */
export const IS_LOCAL = /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(BASE_URL)

/** HTTPS でテストしているか（HSTS のヘッダーは、HTTPS のときだけ返る） */
export const IS_HTTPS = BASE_URL.startsWith('https://')

// import.meta.url は「このファイルの場所」。そこから e2e フォルダの場所を求める
const e2eDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

/** テストの利用者の情報（パスワードとログインの Cookie）を置くフォルダ。Git には入れない（.gitignore） */
// E2E_AUTH_DIR で変えられる（2つの実行を同時に動かすときに、お互いの利用者を消さないようにするため）
export const AUTH_DIR = process.env.E2E_AUTH_DIR ? path.resolve(process.env.E2E_AUTH_DIR) : path.join(e2eDir, '.auth')

/**
 * 【役割】一覧と検索のルール（イベント、コミュニティ）を、偽の API のために再現する関数をまとめたファイル。
 * 【なぜ必要か】要件定義書の「5.8 イベント一覧と検索」「5.11 コミュニティ一覧と検索」のルール
 * （単語の区切り方、並び順、件数の上限など）を、本物のバックエンドができる前に画面で確かめるため。
 * イベントとコミュニティは同じルールなので、共通の関数（rankByWords）で並べる。
 * 本物の検索は、バックエンドが SQL で行う。このファイルは、そのときの見本にもなる。
 */

import type { CommunitySummary } from '../types/community'
import type { EventListResponse, EventSummary } from '../types/event'
import type { MockDb } from './db'
import type { MockCommunity } from './db/communities'
import type { MockEvent } from './db/events'

// 検索のルールで決めた数値。要件定義書 5.8 と同じにしている
export const PAGE_SIZE = 30          // 1回に返す件数
export const MAX_RESULTS = 300       // 検索結果の上限
export const MAX_QUERY_LENGTH = 100  // 入力できる文字数
export const MAX_WORDS = 5           // 使う単語の数

/**
 * 【何のため】入力された文字を、検索に使う単語の一覧に分ける。
 * 例：「 Java（全角の空白）初心者 」 → ["java", "初心者"]
 */
export function splitWords(query: string): string[] {
  return (
    query
      // 100文字を超えた分は使わない
      .slice(0, MAX_QUERY_LENGTH)
      // 空白で区切る。\s は半角の空白やタブ、\u3000 は全角の空白。+ は「1つ以上続く」
      .split(/[\s\u3000]+/)
      // 区切った結果、空になったものを取り除く
      .filter((word) => word !== '')
      // 6つ目以降の単語は使わない
      .slice(0, MAX_WORDS)
      // 英字の大文字と小文字を区別しないように、すべて小文字にそろえる
      .map((word) => word.toLowerCase())
  )
}

/**
 * 【何のため】文の中に、単語がいくつ含まれているかを数える。数が多いほど、検索結果の上のほうに並ぶ。
 */
export function countMatchedWords(text: string, words: string[]): number {
  const lower = text.toLowerCase()
  return words.filter((word) => lower.includes(word)).length
}

/**
 * 【何のため】一覧を、検索のルールどおりに絞り込み、並べ、上限で切る（イベントとコミュニティで共通）。
 * - 検索していないとき：作成日時の新しい順
 * - 検索したとき：1つでも単語を含むものだけ。当てはまった単語の数が多い順、同じなら作成日時の新しい順。300件まで
 * <T> は「一覧の中身の形は、呼び出す側が決める」という印。
 * @param rows 対象の一覧
 * @param query 検索欄に入力された文字（空なら、検索していない）
 * @param textOf 検索の対象になる文（イベントならタイトルと説明）を返す関数
 */
export function rankByWords<T extends { createdAt: string }>(
  rows: T[],
  query: string,
  textOf: (row: T) => string,
): { sorted: T[]; limitExceeded: boolean } {
  const words = splitWords(query)
  // 作成日時の新しい順に並べる関数
  const byNewest = (a: T, b: T) => b.createdAt.localeCompare(a.createdAt)

  // 単語が1つもなければ、「検索していない」として扱う
  if (words.length === 0) {
    // [...rows] で複製してから並べる（元の一覧の順番を変えないため）
    return { sorted: [...rows].sort(byNewest), limitExceeded: false }
  }

  const matched = rows
    // 各行に「当てはまった単語の数」を付ける
    .map((row) => ({ row, score: countMatchedWords(textOf(row), words) }))
    // 1つも当てはまらないものを取り除く
    .filter((item) => item.score > 0)
    // 当てはまった単語の数が多い順。同じなら、作成日時の新しい順
    .sort((a, b) => b.score - a.score || byNewest(a.row, b.row))
    .map((item) => item.row)

  return { sorted: matched.slice(0, MAX_RESULTS), limitExceeded: matched.length > MAX_RESULTS }
}

/**
 * 【何のため】一覧から、指定したページの分を切り出し、ページの情報と一緒に返す。
 */
function slicePage<T>(rows: T[], page: number) {
  const totalCount = rows.length
  // Math.ceil は小数点以下の切り上げ（例：42件 ÷ 30 = 1.4 → 2ページ）
  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE))
  // (ページ番号 - 1) × 30 件目から 30件分
  const start = (page - 1) * PAGE_SIZE
  return { items: rows.slice(start, start + PAGE_SIZE), totalCount, page, pageSize: PAGE_SIZE, totalPages }
}

/**
 * 【何のため】偽のデータベースのイベント（MockEvent）を、画面に返す形（EventSummary）に変える。
 * コミュニティの名前と残りの枠数は、ほかの表（コミュニティ、申込み）から求める。
 * 本物のバックエンドでは、SQL で表をつなぎ合わせて（JOIN）求めることになる。
 */
export function toEventSummary(db: MockDb, event: MockEvent): EventSummary {
  // イベントが属するコミュニティを探す。! は「必ず見つかる」と TypeScript に伝える書き方
  const community = db.communities.find((c) => c.id === event.communityId)!
  // 申込み済み（キャンセル待ちを除く）の人数を数える
  const appliedCount = db.applications.filter((a) => a.eventId === event.id && a.status === 'applied').length

  return {
    id: event.id,
    communityId: event.communityId,
    communityName: community.name,
    title: event.title,
    startAt: event.startAt,
    endAt: event.endAt,
    capacity: event.capacity,
    // 残りの枠数 = 定員 - 申込み済みの人数
    remainingSeats: event.capacity - appliedCount,
  }
}

/**
 * 【何のため】偽のデータベースのコミュニティを、一覧に返す形（CommunitySummary）に変える。
 */
export function toCommunitySummary(db: MockDb, community: MockCommunity): CommunitySummary {
  return {
    id: community.id,
    name: community.name,
    description: community.description,
    memberCount: db.memberships.filter((m) => m.communityId === community.id).length,
    createdAt: community.createdAt,
  }
}

/**
 * 【何のため】イベント一覧・検索の結果を、要件定義書 5.8 のルールどおりに作る。
 * 対象は開催前（終了日時が今より後）のイベントだけ。
 */
export function searchEvents(db: MockDb, query: string, page: number, now: Date): EventListResponse {
  const upcoming = db.events.filter((event) => new Date(event.endAt) > now)
  const { sorted, limitExceeded } = rankByWords(upcoming, query, (e) => `${e.title} ${e.description}`)
  const result = slicePage(sorted, page)
  return { ...result, items: result.items.map((e) => toEventSummary(db, e)), limitExceeded }
}

/**
 * 【何のため】コミュニティ一覧・検索の結果を、要件定義書 5.11 のルールどおりに作る。
 * 対象はアーカイブしていないコミュニティだけ。
 */
export function searchCommunities(db: MockDb, query: string, page: number) {
  const active = db.communities.filter((c) => c.archivedAt === null)
  const { sorted, limitExceeded } = rankByWords(active, query, (c) => `${c.name} ${c.description}`)
  const result = slicePage(sorted, page)
  return { ...result, items: result.items.map((c) => toCommunitySummary(db, c)), limitExceeded }
}

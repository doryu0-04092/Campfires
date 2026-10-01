/**
 * 【役割】イベント一覧と検索のルールを、偽の API のために再現する関数をまとめたファイル。
 * 【なぜ必要か】要件定義書の「5.8 イベント一覧と検索」のルール（並び順、件数の上限など）を、
 * 本物のバックエンドができる前に画面で確かめるため。
 * 本物の検索は、バックエンドが SQL で行う。このファイルは、そのときの見本にもなる。
 */

import type { EventListResponse, EventSummary } from '../types/event'
import type { MockEvent } from './data/events'

// 検索のルールで決めた数値。要件定義書 5.8 と同じにしている
export const PAGE_SIZE = 30          // 1ページの件数
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
 * 【何のため】イベントのタイトルと説明に、単語がいくつ含まれているかを数える。
 * 数が多いほど、検索結果の上のほうに並ぶ。
 */
export function countMatchedWords(event: MockEvent, words: string[]): number {
  // タイトルと説明をつなげて、小文字にそろえる
  const text = `${event.title} ${event.description}`.toLowerCase()
  // 含まれている単語だけを残して、その数を返す
  return words.filter((word) => text.includes(word)).length
}

/**
 * 【何のため】偽のデータベースのイベント（MockEvent）を、画面に返す形（EventSummary）に変える。
 * 説明など、一覧で使わない項目はここで取り除く。
 */
function toSummary(event: MockEvent): EventSummary {
  return {
    id: event.id,
    communityId: event.communityId,
    communityName: event.communityName,
    title: event.title,
    startAt: event.startAt,
    endAt: event.endAt,
    capacity: event.capacity,
    // 残りの枠数 = 定員 - 申込み済みの人数
    remainingSeats: event.capacity - event.appliedCount,
  }
}

/**
 * 【何のため】イベント一覧・検索の結果を、要件定義書 5.8 のルールどおりに作る。
 * @param events 偽のデータベースにある、すべてのイベント
 * @param query 検索欄に入力された文字（空なら、検索していない）
 * @param page 表示したいページ番号
 * @param now 今の日時（終了したイベントを除くために使う）
 */
export function searchEvents(
  events: MockEvent[],
  query: string,
  page: number,
  now: Date,
): EventListResponse {
  // 開催前（終了日時が今より後）のイベントだけを残す
  const upcoming = events.filter((event) => new Date(event.endAt) > now)

  const words = splitWords(query)
  // 単語が1つもなければ、「検索していない」として扱う
  const isSearching = words.length > 0

  // 並び替えた結果を入れる入れ物。let は、あとで中身を入れ替える変数に使う
  let sorted: MockEvent[]

  if (isSearching) {
    sorted = upcoming
      // 各イベントに「当てはまった単語の数」を付ける
      .map((event) => ({ event, score: countMatchedWords(event, words) }))
      // 1つも当てはまらないイベントを取り除く
      .filter((item) => item.score > 0)
      // 並び替え：当てはまった単語の数が多い順。同じなら、作成日時の新しい順
      .sort((a, b) => b.score - a.score || b.event.createdAt.localeCompare(a.event.createdAt))
      // 並び替えが終わったら、イベントだけを取り出す
      .map((item) => item.event)
  } else {
    // 検索していないときは、作成日時の新しい順に並べるだけ。
    // [...upcoming] で複製してから並べる（元の一覧の順番を変えないため）
    sorted = [...upcoming].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  }

  // 検索しているときは、300件を上限にする
  const limitExceeded = isSearching && sorted.length > MAX_RESULTS
  const limited = isSearching ? sorted.slice(0, MAX_RESULTS) : sorted

  // ページの計算。Math.ceil は小数点以下の切り上げ（例：42件 ÷ 30 = 1.4 → 2ページ）
  const totalCount = limited.length
  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE))
  // 表示するのは、(ページ番号 - 1) × 30 件目から 30件分
  const start = (page - 1) * PAGE_SIZE
  const items = limited.slice(start, start + PAGE_SIZE).map(toSummary)

  return { items, totalCount, page, pageSize: PAGE_SIZE, totalPages, limitExceeded }
}

/**
 * 【役割】イベントに関する API を呼び出す関数をまとめたファイル。
 * 【なぜ必要か】API の URL や呼び出し方を画面のコードに直接書かず、ここに集めておくため。
 * バックエンドの API が変わったときは、このファイルだけを直せばよい。
 * 今は偽の API（MSW）が返事をし、バックエンドができたら本物の API が返事をする。どちらでもこのファイルは同じ。
 */

import type { EventListResponse } from '../types/event'

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
 * async を付けた関数は、結果を「あとで届くもの」（Promise）として返す。
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

  // fetch：ブラウザに備わっている、API を呼び出すための関数。await で返事が届くまで待つ
  const response = await fetch(`/api/events?${params.toString()}`, { signal })

  // response.ok は、返事が成功（200番台）かどうか。失敗なら、エラーとして呼び出し元に知らせる
  if (!response.ok) {
    throw new Error(`イベントの取得に失敗しました（${response.status}）`)
  }

  // 返事の中身（JSON）を、決めておいた形（EventListResponse）として受け取る。
  // as は「この形だと信じて扱う」という書き方。形が違っても TypeScript は気づけないので、
  // 本物の API とつなぐときに、API 仕様書どおりの形かを確かめる
  return (await response.json()) as EventListResponse
}

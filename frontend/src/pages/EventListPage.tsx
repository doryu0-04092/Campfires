/**
 * 【役割】S-01「イベント一覧（トップ）」の画面。URL は /
 * 【なぜ必要か】アプリを開いて最初に表示される画面。イベントを一覧で表示し、検索もここで行う（要件定義書 5.8）。
 * - ログインしていて、検索していないとき：自分が申し込んだ開催前のイベントを、全件表示する
 * - 検索したとき・ログインしていないとき：開催前のイベントを、最初に 30件、スクロールで 30件ずつ足して表示する
 *
 * 検索の文字は URL に入れる（例：/?q=Java）。検索結果の URL を人に送ったり、「戻る」で前の結果に戻ったりできる。
 */

import { useCallback } from 'react'
import { Link, useSearchParams } from 'react-router'
import { fetchEvents, fetchMyEvents } from '../api/events'
import { useAuth } from '../auth/useAuth'
import EventCard from '../components/EventCard'
import InfiniteListFooter from '../components/InfiniteListFooter'
import styles from '../components/List.module.css'
import LoadError from '../components/LoadError'
import SearchForm from '../components/SearchForm'
import type { EventListResponse, EventSummary } from '../types/event'
import { useInfiniteList } from '../utils/useInfiniteList'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】イベント一覧（トップ）の画面を表示する。
 */
function EventListPage() {
  // URL から検索の文字を読み取る。setSearchParams で URL を書き換えられる
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const { state } = useAuth()

  /**
   * 【何のため】検索ボタンが押されたときの処理。URL を書き換えると、一覧が変わる。
   */
  function handleSearch(newQuery: string) {
    setSearchParams(newQuery === '' ? {} : { q: newQuery })
  }

  return (
    <section>
      <h1 className={styles.header}>イベントを探す</h1>

      {/* key={query}：URL の検索の文字が変わったら（「戻る」を押したときなど）、検索欄を作り直して、
          入力欄の文字を URL の文字にそろえる */}
      <SearchForm
        key={query}
        id="event-search"
        label="イベントを検索"
        placeholder="キーワードで探す（例：Java 初心者）"
        initialQuery={query}
        onSearch={handleSearch}
      />

      {/* ログインしているか確かめている途中は、どちらの一覧を出すか決まらないので待つ */}
      {state.status === 'loading' && <p className={styles.message}>読み込み中…</p>}
      {state.status === 'authenticated' && query === '' && <MyEvents />}
      {state.status !== 'loading' && (query !== '' || state.status === 'guest') && <AllEvents query={query} />}
    </section>
  )
}

/**
 * 【何のため】自分が申し込んだ開催前のイベントを、全件表示する（キャンセル待ちを含む）。
 */
function MyEvents() {
  const { isLoading, data, error } = useLoader('my-events', (signal) => fetchMyEvents(signal))

  return (
    <>
      <h2>参加予定のイベント</h2>
      {isLoading && <p className={styles.message}>読み込み中…</p>}
      {error !== undefined && <LoadError error={error} />}
      {data && data.items.length === 0 && (
        <p className={styles.message}>
          参加予定のイベントはありません。上の検索欄や、<Link to="/communities">コミュニティ一覧</Link>から探してみましょう。
        </p>
      )}
      {data && data.items.length > 0 && (
        <>
          <p className={styles.message}>{data.items.length}件（開催日の近い順）</p>
          <ul className={styles.list}>
            {data.items.map((event) => (
              <li key={event.id}>
                <EventCard event={event} myApplication={event.myApplication} />
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  )
}

/**
 * 【何のため】すべての開催前のイベント、または検索結果を、最初に 30件、スクロールで 30件ずつ足して表示する。
 */
function AllEvents({ query }: { query: string }) {
  // useCallback で、検索の文字が変わったときだけ、読み込む関数を作り直す
  const fetchPage = useCallback((page: number) => fetchEvents({ query, page }), [query])
  const list = useInfiniteList<EventSummary, EventListResponse>(`events:${query}`, fetchPage)
  const isSearching = query !== ''

  return (
    <>
      {list.last && (
        <p className={styles.message}>
          {isSearching ? `「${query}」の検索結果：${list.last.totalCount}件見つかりました` : `開催前のイベント：${list.last.totalCount}件`}
        </p>
      )}
      {/* 検索結果が 300件を超えたときの案内 */}
      {list.last?.limitExceeded && (
        <p className={styles.banner}>300件以上見つかりました。上位300件を表示しています。単語を増やして絞り込んでください。</p>
      )}
      {list.isFirstLoaded && list.items.length === 0 && (
        <p className={styles.message}>
          {isSearching ? '当てはまるイベントが見つかりませんでした。' : '開催前のイベントはまだありません。'}
        </p>
      )}

      <ul className={styles.list}>
        {list.items.map((event) => (
          <li key={event.id}>
            <EventCard event={event} />
          </li>
        ))}
      </ul>

      <InfiniteListFooter list={list} sentinelRef={list.sentinelRef} />
    </>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default EventListPage

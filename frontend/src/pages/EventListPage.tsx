/**
 * 【役割】S-01「イベント一覧（トップ）」の画面。URL は /
 * 【なぜ必要か】アプリを開いて最初に表示される画面。開催前のイベントを一覧で表示し、検索もここで行う。
 * 表示の並び順や検索のルールは、要件定義書の「5.8 イベント一覧と検索」で決めている。
 *
 * 検索の文字とページ番号は URL に入れる（例：/?q=Java&page=2）。
 * そうすると、検索結果の URL を人に送ったり、ブラウザの「戻る」で前の結果に戻ったりできる。
 */

// useEffect：画面を表示したあとに行う処理（ここでは API の呼び出し）を書くための関数
// useState：部品の中で、変わる値を覚えておくための関数
import { useEffect, useState } from 'react'
// useSearchParams：URL の「?」より後ろの部分（q や page）を読み書きするための関数
import { useSearchParams } from 'react-router'
import { fetchEvents } from '../api/events'
import EventCard from '../components/EventCard'
import Pagination from '../components/Pagination'
import SearchForm from '../components/SearchForm'
import type { EventListResponse } from '../types/event'
import styles from './EventListPage.module.css'

/**
 * 【何のため】API を呼んだ結果を表す形。成功か失敗のどちらかで、成功のときだけ data（結果）を持つ。
 * | は「どれか1つ」という意味。
 * key には「どの検索条件の結果か」を入れておく。今の条件と違えば、まだ読み込み中だと分かる
 */
type LoadResult =
  | { key: string; status: 'success'; data: EventListResponse }
  | { key: string; status: 'error' }

/**
 * 【何のため】検索の文字とページ番号から、「どの検索条件か」を表す文字を作る。
 */
function toRequestKey(query: string, page: number): string {
  return `${page}:${query}`
}

/**
 * 【何のため】URL の page の値を、ページ番号として使える数に変える。
 * 数でない、または 1 より小さいときは 1 にする（URL を手で書き換えられても壊れないようにするため）。
 */
function parsePage(value: string | null): number {
  const page = Number(value ?? '1')
  return Number.isInteger(page) && page >= 1 ? page : 1
}

/**
 * 【何のため】検索の文字とページ番号から、この画面の URL を作る。
 * 例：("Java", 2) → "/?q=Java&page=2"、("", 1) → "/"
 */
function buildListHref(query: string, page: number): string {
  const params = new URLSearchParams()
  if (query !== '') {
    params.set('q', query)
  }
  if (page > 1) {
    params.set('page', String(page))
  }
  const search = params.toString()
  return search === '' ? '/' : `/?${search}`
}

/**
 * 【何のため】イベント一覧（トップ）の画面を表示する。
 */
function EventListPage() {
  // URL から検索の文字とページ番号を読み取る。setSearchParams で URL を書き換えられる
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const page = parsePage(searchParams.get('page'))

  // 最後に届いた API の結果。最初はまだ何も届いていないので null（空）
  const [loadResult, setLoadResult] = useState<LoadResult | null>(null)

  // 今の検索条件を表す文字。届いた結果の key と比べて、読み込み中かどうかを判断する
  const requestKey = toRequestKey(query, page)
  // 結果がまだない、または届いている結果が前の検索条件のものなら、読み込み中とみなす。
  // 「読み込み中」を別の値として覚えずに、ここで計算して求める（覚える値を増やすと、食い違いが起きやすいため）
  const isLoading = loadResult === null || loadResult.key !== requestKey

  // 検索の文字かページ番号が変わるたびに、API を呼んで一覧を取り直す。
  // 最後の [query, page] は「この値が変わったときにだけ、もう一度実行する」という指定
  useEffect(() => {
    // この呼び出しが、どの検索条件のものかを覚えておく
    const key = toRequestKey(query, page)
    // 通信を途中で取りやめるための道具。
    // 結果が届く前に次の検索をしたとき、古い結果で画面が上書きされないようにする
    const controller = new AbortController()

    fetchEvents({ query, page, signal: controller.signal })
      // 成功したら、結果を覚える
      .then((data) => setLoadResult({ key, status: 'success', data }))
      // 失敗したら、失敗したことを覚える。ただし、自分で取りやめた通信（AbortError）は無視する
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return
        }
        setLoadResult({ key, status: 'error' })
      })

    // この画面を離れるときや、次の検索が始まるときに、前の通信を取りやめる
    return () => controller.abort()
  }, [query, page])

  /**
   * 【何のため】検索ボタンが押されたときの処理。URL を書き換えると、上の useEffect が動いて一覧が変わる。
   * 新しく検索したときは、1ページ目から表示する（page は URL に入れない）。
   */
  function handleSearch(newQuery: string) {
    setSearchParams(newQuery === '' ? {} : { q: newQuery })
  }

  return (
    <section>
      <h1 className={styles.heading}>イベントを探す</h1>

      {/* key={query}：URL の検索の文字が変わったら（「戻る」を押したときなど）、検索欄を作り直して、
          入力欄の文字を URL の文字にそろえる */}
      <SearchForm key={query} initialQuery={query} onSearch={handleSearch} />

      {/* 読み込み中 */}
      {isLoading && <p className={styles.message}>読み込み中…</p>}

      {/* 失敗したとき */}
      {!isLoading && loadResult.status === 'error' && (
        // role="alert"：読み上げソフトに、すぐに読み上げるべき大事な知らせだと伝える
        <p className={styles.error} role="alert">
          イベントを読み込めませんでした。時間をおいて、もう一度お試しください。
        </p>
      )}

      {/* 成功したとき */}
      {!isLoading && loadResult.status === 'success' && (
        <EventListResult
          data={loadResult.data}
          query={query}
          buildHref={(pageNumber) => buildListHref(query, pageNumber)}
        />
      )}
    </section>
  )
}

/**
 * 【何のため】EventListResult が受け取る値の形を決める。
 */
type EventListResultProps = {
  data: EventListResponse
  query: string
  buildHref: (page: number) => string
}

/**
 * 【何のため】API の結果（件数、イベントの一覧、ページの切り替え）を表示する。
 * 画面の本体が長くならないように、結果の表示だけをこの関数に分けている。
 */
function EventListResult({ data, query, buildHref }: EventListResultProps) {
  const isSearching = query !== ''

  return (
    <>
      {/* 件数の表示。検索しているかどうかで、文を変える */}
      <p className={styles.message}>
        {isSearching
          ? `「${query}」の検索結果：${data.totalCount}件見つかりました`
          : `開催前のイベント：${data.totalCount}件`}
      </p>

      {/* 検索結果が 300件を超えたときの案内 */}
      {data.limitExceeded && (
        <p className={styles.notice}>
          300件以上見つかりました。上位300件を表示しています。単語を増やして絞り込んでください。
        </p>
      )}

      {/* イベントが1件もないとき */}
      {data.items.length === 0 ? (
        <p className={styles.message}>
          {isSearching ? '当てはまるイベントが見つかりませんでした。' : '開催前のイベントはまだありません。'}
        </p>
      ) : (
        // イベントを1件ずつ、カードの形で並べる
        <ul className={styles.list}>
          {data.items.map((event) => (
            <li key={event.id}>
              <EventCard event={event} />
            </li>
          ))}
        </ul>
      )}

      {/* ページの切り替え */}
      <Pagination page={data.page} totalPages={data.totalPages} buildHref={buildHref} />
    </>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default EventListPage

/**
 * 【役割】S-17「コミュニティ一覧」の画面。URL は /communities
 * 【なぜ必要か】自分が参加しているコミュニティを見て、新しいコミュニティを探すため（F-04、要件定義書 5.11）。
 * - ログインしていて、検索していないとき：自分がメンバーとして参加しているコミュニティを、全件表示する
 * - 検索したとき・ログインしていないとき：すべてのコミュニティを、最初に 30件、スクロールで 30件ずつ足して表示する
 * 利用者のための画面なので、コミュニティを作るボタンは置かない（作成は、管理の画面から行う）。
 */

import { useCallback } from 'react'
import { Link, useSearchParams } from 'react-router'
import { fetchCommunities, fetchMyCommunities } from '../api/communities'
import { useAuth } from '../auth/useAuth'
import InfiniteListFooter from '../components/InfiniteListFooter'
import styles from '../components/List.module.css'
import LoadError from '../components/LoadError'
import SearchForm from '../components/SearchForm'
import type { CommunityListResponse, CommunitySummary } from '../types/community'
import { useInfiniteList } from '../utils/useInfiniteList'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】コミュニティ一覧の画面を表示する。
 */
function CommunityListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const { state } = useAuth()

  return (
    <section>
      <h1 className={styles.header}>コミュニティ一覧</h1>

      <SearchForm
        key={query}
        id="community-search"
        label="コミュニティを検索"
        placeholder="キーワードで探す（例：Java 勉強会）"
        initialQuery={query}
        onSearch={(newQuery) => setSearchParams(newQuery === '' ? {} : { q: newQuery })}
      />

      {state.status === 'loading' && <p className={styles.message}>読み込み中…</p>}
      {state.status === 'authenticated' && query === '' && <MyCommunities />}
      {state.status !== 'loading' && (query !== '' || state.status === 'guest') && <AllCommunities query={query} />}
    </section>
  )
}

/**
 * 【何のため】コミュニティ1件分のカードを表示する。
 */
function CommunityCard({ community }: { community: CommunitySummary }) {
  return (
    <li className={styles.card}>
      <h2 className={styles.cardTitle}>
        <Link to={`/communities/${community.id}`}>{community.name}</Link>
      </h2>
      {/* 説明は長いことがあるので、3行までで省略する */}
      {community.description && <p className={`${styles.cardMeta} ${styles.clamp}`}>{community.description}</p>}
      <p className={styles.cardMeta}>メンバー {community.memberCount}人</p>
    </li>
  )
}

/**
 * 【何のため】自分がメンバーとして参加しているコミュニティを、全件表示する。
 * オーナーや運営スタッフをしているコミュニティは、「管理しているコミュニティ」に出るので、ここには出さない。
 */
function MyCommunities() {
  const { isLoading, data, error } = useLoader('my-communities', (signal) => fetchMyCommunities(signal))

  return (
    <>
      <h2>参加しているコミュニティ</h2>
      {isLoading && <p className={styles.message}>読み込み中…</p>}
      {error !== undefined && <LoadError error={error} />}
      {data && data.items.length === 0 && (
        <p className={styles.message}>メンバーとして参加しているコミュニティはありません。上の検索欄から探してみましょう。</p>
      )}
      {data && data.items.length > 0 && (
        <>
          <p className={styles.message}>{data.items.length}件</p>
          <ul className={styles.list}>
            {data.items.map((community) => (
              <CommunityCard key={community.id} community={community} />
            ))}
          </ul>
        </>
      )}
    </>
  )
}

/**
 * 【何のため】すべてのコミュニティ、または検索結果を、最初に 30件、スクロールで 30件ずつ足して表示する。
 */
function AllCommunities({ query }: { query: string }) {
  const fetchPage = useCallback((page: number) => fetchCommunities(query, page), [query])
  const list = useInfiniteList<CommunitySummary, CommunityListResponse>(`communities:${query}`, fetchPage)
  const isSearching = query !== ''

  return (
    <>
      {list.last && (
        <p className={styles.message}>
          {isSearching ? `「${query}」の検索結果：${list.last.totalCount}件見つかりました` : `${list.last.totalCount}件のコミュニティ`}
        </p>
      )}
      {list.last?.limitExceeded && (
        <p className={styles.banner}>300件以上見つかりました。上位300件を表示しています。単語を増やして絞り込んでください。</p>
      )}
      {list.isFirstLoaded && list.items.length === 0 && (
        <p className={styles.message}>{isSearching ? '当てはまるコミュニティが見つかりませんでした。' : 'コミュニティはまだありません。'}</p>
      )}
      <ul className={styles.list}>
        {list.items.map((community) => (
          <CommunityCard key={community.id} community={community} />
        ))}
      </ul>
      <InfiniteListFooter list={list} sentinelRef={list.sentinelRef} />
    </>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default CommunityListPage

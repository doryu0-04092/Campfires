/**
 * 【役割】S-17「コミュニティ一覧」の画面。URL は /communities
 * 【なぜ必要か】すべてのコミュニティを探せるようにするため（F-04）。
 * トップの画面（S-01）はイベントの一覧なので、まだイベントを開いていないコミュニティは、ここから見つける。
 * アーカイブ済みのコミュニティは表示しない（要件定義書 5.9）。ログインしていなくても見られる。
 */

import { Link, useSearchParams } from 'react-router'
import { fetchCommunities } from '../api/communities'
import styles from '../components/List.module.css'
import LoadError from '../components/LoadError'
import Pagination from '../components/Pagination'
import { pageHref, parsePage } from '../utils/page'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】コミュニティ一覧の画面を表示する。
 */
function CommunityListPage() {
  const [searchParams] = useSearchParams()
  const page = parsePage(searchParams.get('page'))
  // ページ番号が変わるたびに、一覧を読み込み直す
  const { isLoading, data, error } = useLoader(`communities:${page}`, (signal) => fetchCommunities(page, signal))

  return (
    <section>
      <div className={styles.header}>
        <h1>コミュニティ一覧</h1>
        <Link to="/manage/communities/new" className={`${styles.button} ${styles.buttonPrimary}`}>
          コミュニティを作る
        </Link>
      </div>

      {isLoading && <p className={styles.message}>読み込み中…</p>}
      {error !== undefined && <LoadError error={error} />}

      {data && (
        <>
          <p className={styles.message}>{data.totalCount}件のコミュニティ</p>
          {data.items.length === 0 ? (
            <p className={styles.message}>コミュニティはまだありません。</p>
          ) : (
            <ul className={styles.list}>
              {data.items.map((community) => (
                <li key={community.id} className={styles.card}>
                  <h2 className={styles.cardTitle}>
                    <Link to={`/communities/${community.id}`}>{community.name}</Link>
                  </h2>
                  {/* 説明は長いことがあるので、3行までで省略する */}
                  {community.description && (
                    <p className={`${styles.cardMeta} ${styles.clamp}`}>{community.description}</p>
                  )}
                  <p className={styles.cardMeta}>メンバー {community.memberCount}人</p>
                </li>
              ))}
            </ul>
          )}
          <Pagination page={data.page} totalPages={data.totalPages} buildHref={(p) => pageHref('/communities', p)} />
        </>
      )}
    </section>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default CommunityListPage

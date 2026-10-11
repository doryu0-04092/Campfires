/**
 * 【役割】S-16「管理しているコミュニティ一覧」の画面。URL は /manage/communities
 * 【なぜ必要か】管理の画面の入り口。自分がオーナーか運営スタッフのコミュニティを一覧にし、
 * そこから各コミュニティの管理の画面（編集、メンバー管理、イベント作成）へ進む。
 * アーカイブ済みのコミュニティも表示し、オーナーには「元に戻す」ボタンを出す（要件定義書 5.9）。
 */

import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { fetchManagedCommunities, unarchiveCommunity } from '../api/communities'
import formStyles from '../components/Form.module.css'
import styles from '../components/List.module.css'
import LoadError from '../components/LoadError'
import Pagination from '../components/Pagination'
import { ROLE_LABELS, type ManagedCommunity } from '../types/community'
import { pageHref, parsePage } from '../utils/page'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】管理しているコミュニティ一覧の画面を表示する。
 */
function ManagedCommunityListPage() {
  const [searchParams] = useSearchParams()
  const page = parsePage(searchParams.get('page'))
  const { isLoading, data, error, reload } = useLoader(`managed:${page}`, (signal) => fetchManagedCommunities(page, signal))

  return (
    <section>
      <div className={styles.header}>
        <h1>管理しているコミュニティ</h1>
        <Link to="/manage/communities/new" className={`${styles.button} ${styles.buttonPrimary}`}>
          コミュニティを作る
        </Link>
      </div>

      {isLoading && <p className={styles.message}>読み込み中…</p>}
      {error !== undefined && <LoadError error={error} />}

      {data && (
        <>
          {data.items.length === 0 ? (
            <p className={styles.message}>オーナーか運営スタッフをしているコミュニティは、まだありません。</p>
          ) : (
            <ul className={styles.list}>
              {data.items.map((community) => (
                <ManagedCommunityCard key={community.id} community={community} onChanged={reload} />
              ))}
            </ul>
          )}
          <Pagination page={data.page} totalPages={data.totalPages} buildHref={(p) => pageHref('/manage/communities', p)} />
        </>
      )}
    </section>
  )
}

/**
 * 【何のため】管理しているコミュニティ1件分のカードを表示する。自分の立場によって、出す操作を変える。
 */
function ManagedCommunityCard({ community, onChanged }: { community: ManagedCommunity; onChanged: () => void }) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const base = `/manage/communities/${community.id}`
  const isOwner = community.myRole === 'owner'

  /**
   * 【何のため】アーカイブを元に戻す。
   */
  async function handleUnarchive() {
    setIsSubmitting(true)
    setError(null)
    try {
      await unarchiveCommunity(community.id)
      onChanged()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '通信中にエラーが発生しました。')
      setIsSubmitting(false)
    }
  }

  return (
    <li className={styles.card}>
      <h2 className={styles.cardTitle}>
        <Link to={`/communities/${community.id}`}>{community.name}</Link>
        <span className={styles.badge}>{ROLE_LABELS[community.myRole]}</span>
        {community.archivedAt && <span className={`${styles.badge} ${styles.badgeMuted}`}>アーカイブ済み</span>}
      </h2>
      <p className={styles.cardMeta}>
        メンバー {community.memberCount}人
        {/* 承認を待っている参加申請があれば、目立たせる */}
        {community.pendingJoinRequestCount > 0 && (
          <span className={`${styles.badge} ${styles.badgeAccent}`}>承認待ち {community.pendingJoinRequestCount}件</span>
        )}
      </p>
      <div className={styles.actions}>
        {isOwner && <Link to={`${base}/edit`} className={styles.button}>編集</Link>}
        <Link to={`${base}/members`} className={styles.button}>メンバー管理</Link>
        {!community.archivedAt && <Link to={`${base}/events/new`} className={styles.button}>イベントを作る</Link>}
        {isOwner && community.archivedAt && (
          <button type="button" className={styles.button} onClick={handleUnarchive} disabled={isSubmitting}>
            元に戻す
          </button>
        )}
      </div>
      {error && (
        <p className={formStyles.formError} role="alert">
          {error}
        </p>
      )}
    </li>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default ManagedCommunityListPage

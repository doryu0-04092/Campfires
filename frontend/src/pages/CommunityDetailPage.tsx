/**
 * 【役割】S-05「コミュニティ詳細」の画面。URL は /communities/:communityId
 * 【なぜ必要か】コミュニティの情報を見て、参加の申請（F-06）や退会（F-08）をするため。
 * 見ている人の立場（API が返す myRole、myJoinRequestStatus）によって、出すボタンを変える。
 * ただし、ボタンを出し分けるのは使いやすさのためで、本当の権限のチェックはバックエンド（偽の API）が行う。
 * 下のほうに、このコミュニティのイベントの一覧（開催前・終了済み）を表示する。
 */

import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { fetchCommunity, leaveCommunity, requestToJoin } from '../api/communities'
import { fetchCommunityEvents } from '../api/events'
import EventCard from '../components/EventCard'
import { useAuth } from '../auth/useAuth'
import formStyles from '../components/Form.module.css'
import styles from '../components/List.module.css'
import LoadError from '../components/LoadError'
import { ROLE_LABELS, type CommunityDetail } from '../types/community'
import { displayName } from '../types/user'
import { formatDateTime } from '../utils/formatDateTime'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】コミュニティ詳細の画面を表示する。
 */
function CommunityDetailPage() {
  // URL からコミュニティの番号を取り出す
  const communityId = Number(useParams().communityId)
  const { state } = useAuth()
  // ログイン状態が変わったら（ログイン・ログアウト）、自分との関係が変わるので読み込み直す
  const { isLoading, data, error, reload } = useLoader(`community:${communityId}:${state.status}`, (signal) =>
    fetchCommunity(communityId, signal),
  )

  if (isLoading) {
    return <p className={styles.message}>読み込み中…</p>
  }
  if (error !== undefined || !data) {
    return <LoadError error={error} />
  }

  return (
    <section>
      <h1>
        {data.name}
        {data.archivedAt && <span className={`${styles.badge} ${styles.badgeMuted}`}>アーカイブ済み</span>}
      </h1>

      {/* アーカイブ済みのときの知らせ（要件定義書 5.9） */}
      {data.archivedAt && (
        <p className={styles.banner}>
          このコミュニティはアーカイブされています（{formatDateTime(data.archivedAt)}）。新しい参加の申請はできません。
        </p>
      )}

      {/* white-space: pre-wrap で、説明の中の改行をそのまま表示する */}
      {data.description && <p style={{ whiteSpace: 'pre-wrap' }}>{data.description}</p>}

      <dl className={styles.cardMeta}>
        <div>オーナー：{displayName(data.owner)}</div>
        <div>メンバー：{data.memberCount}人</div>
        {data.myRole && <div>あなたの立場：{ROLE_LABELS[data.myRole]}</div>}
      </dl>

      {/* key に状態を渡すと、状態が変わったときに中のメッセージが消える */}
      <MembershipActions key={`${data.myRole}:${data.myJoinRequestStatus}`} community={data} onChanged={reload} />
      <ManageLinks community={data} />

      <CommunityEvents communityId={data.id} />
    </section>
  )
}

/**
 * 【何のため】コミュニティのイベントの一覧を、「開催前」と「終了済み」に切り替えて表示する。
 * 終了したイベントは、トップのイベント一覧には出さないので、ここで見られるようにしている（要件定義書 5.8）。
 */
function CommunityEvents({ communityId }: { communityId: number }) {
  // どちらを表示しているか。最初は開催前
  const [status, setStatus] = useState<'upcoming' | 'past'>('upcoming')
  const [page, setPage] = useState(1)
  const { isLoading, data, error } = useLoader(`community-events:${communityId}:${status}:${page}`, (signal) =>
    fetchCommunityEvents(communityId, status, page, signal),
  )

  /**
   * 【何のため】開催前・終了済みを切り替える。切り替えたら1ページ目に戻す。
   */
  function changeStatus(next: 'upcoming' | 'past') {
    setStatus(next)
    setPage(1)
  }

  return (
    <div>
      <h2>イベント</h2>
      {/* role="tablist"・"tab"・aria-selected：読み上げソフトに、切り替えのタブであることと、選ばれているほうを伝える */}
      <div className={styles.actions} role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={status === 'upcoming'}
          className={`${styles.button} ${status === 'upcoming' ? styles.buttonPrimary : ''}`}
          onClick={() => changeStatus('upcoming')}
        >
          開催前
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={status === 'past'}
          className={`${styles.button} ${status === 'past' ? styles.buttonPrimary : ''}`}
          onClick={() => changeStatus('past')}
        >
          終了済み
        </button>
      </div>

      {isLoading && <p className={styles.message}>読み込み中…</p>}
      {error !== undefined && <LoadError error={error} />}
      {data && (
        <>
          {data.items.length === 0 ? (
            <p className={styles.message}>{status === 'upcoming' ? '開催前のイベントはありません。' : '終了したイベントはありません。'}</p>
          ) : (
            <ul className={styles.list}>
              {data.items.map((event) => (
                <li key={event.id}>
                  <EventCard event={event} />
                </li>
              ))}
            </ul>
          )}
          {/* ページの切り替えは、URL ではなくこの画面の中だけで行うので、ボタンにしている */}
          {data.totalPages > 1 && (
            <div className={styles.actions}>
              <button type="button" className={styles.button} onClick={() => setPage(page - 1)} disabled={page <= 1}>
                前へ
              </button>
              <span className={styles.cardMeta}>
                {page} / {data.totalPages}
              </span>
              <button type="button" className={styles.button} onClick={() => setPage(page + 1)} disabled={page >= data.totalPages}>
                次へ
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

/**
 * 【何のため】参加の申請と退会のボタンを表示する。見ている人の立場によって、出すものを変える。
 */
function MembershipActions({ community, onChanged }: { community: CommunityDetail; onChanged: () => void }) {
  const { state } = useAuth()
  const location = useLocation()
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  /**
   * 【何のため】API を呼び、成功したら読み込み直す。失敗したら、サーバーの文を出す。
   */
  async function run(action: () => Promise<unknown>) {
    setIsSubmitting(true)
    setError(null)
    try {
      await action()
      onChanged()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '通信中にエラーが発生しました。時間をおいて、もう一度お試しください。')
      setIsSubmitting(false)
    }
  }

  /**
   * 【何のため】退会のボタンが押されたときの処理。間違って押さないように、確認してから退会する（要件定義書 5.4）。
   */
  function handleLeave() {
    // window.confirm は、ブラウザの確認の窓を出す。「OK」なら true、「キャンセル」なら false
    const ok = window.confirm(
      `「${community.name}」を退会しますか？\n退会すると、このコミュニティの開催前のイベントへの申込み（キャンセル待ちを含む）は、すべてキャンセルされます。`,
    )
    if (ok) {
      void run(() => leaveCommunity(community.id))
    }
  }

  // ログインしていない：ログインを案内する。ログインしたら、この画面に戻ってくる
  if (state.status !== 'authenticated') {
    return (
      <p className={styles.message}>
        参加を申請するには、<Link to="/login" state={{ from: location }}>ログイン</Link>してください。
      </p>
    )
  }

  let content
  if (community.myRole === 'owner') {
    // オーナーは退会できない（要件定義書 5.1）
    content = <p className={styles.message}>あなたはこのコミュニティのオーナーです。</p>
  } else if (community.myRole !== null) {
    // メンバーと運営スタッフ：退会できる
    content = (
      <button type="button" className={`${styles.button} ${styles.buttonDanger}`} onClick={handleLeave} disabled={isSubmitting}>
        このコミュニティを退会する
      </button>
    )
  } else if (community.archivedAt) {
    // アーカイブ中は、参加を申請できない
    content = null
  } else if (community.myJoinRequestStatus === 'pending') {
    content = <p className={styles.message}>参加を申請しています。オーナーか運営スタッフの承認を待っています。</p>
  } else {
    // まだ申請していない、または却下された：申請できる（却下された人も、もう一度申請できる。要件定義書 5.1）
    content = (
      <>
        {community.myJoinRequestStatus === 'rejected' && (
          <p className={styles.message}>前回の参加の申請は、承認されませんでした。もう一度申請できます。</p>
        )}
        <button
          type="button"
          className={`${styles.button} ${styles.buttonPrimary}`}
          onClick={() => void run(() => requestToJoin(community.id))}
          disabled={isSubmitting}
        >
          参加を申請する
        </button>
      </>
    )
  }

  return (
    <div className={styles.actions}>
      {content}
      {error && (
        <p className={formStyles.formError} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

/**
 * 【何のため】オーナーと運営スタッフに、このコミュニティの管理の画面へのリンクを表示する。
 */
function ManageLinks({ community }: { community: CommunityDetail }) {
  if (community.myRole !== 'owner' && community.myRole !== 'staff') {
    return null
  }
  const base = `/manage/communities/${community.id}`
  return (
    <div className={styles.actions}>
      <span className={styles.cardMeta}>管理：</span>
      {/* 編集（アーカイブを含む）はオーナーだけ */}
      {community.myRole === 'owner' && (
        <Link to={`${base}/edit`} className={styles.button}>コミュニティを編集</Link>
      )}
      <Link to={`${base}/members`} className={styles.button}>メンバー管理</Link>
      {/* アーカイブ中は、新しいイベントを作れない（要件定義書 5.9） */}
      {!community.archivedAt && (
        <Link to={`${base}/events/new`} className={styles.button}>イベントを作る</Link>
      )}
    </div>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default CommunityDetailPage

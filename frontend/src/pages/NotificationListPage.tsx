/**
 * 【役割】S-15「お知らせ一覧」の画面。URL は /notifications
 * 【なぜ必要か】締め切り後のキャンセルや、イベントの削除のお知らせを確認するため（F-20、要件定義書 5.10）。
 * - 新しい順に、最初に 30件を表示し、下までスクロールすると次の 30件を足して表示する
 * - 既読になったお知らせも、消さずに残す
 * - お知らせを開くと既読になり、イベントがあればイベントの詳細へ移動する
 * 既読にしたら、画面上部の未読の件数も取り直してもらう。
 */

import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { fetchNotifications, markAllAsRead, markAsRead } from '../api/notifications'
import formStyles from '../components/Form.module.css'
import styles from '../components/List.module.css'
import LoadError from '../components/LoadError'
import { useLayoutContext } from '../components/layoutContext'
import type { Notification } from '../types/notification'
import { displayName } from '../types/user'
import { formatDateTime } from '../utils/formatDateTime'

/**
 * 【何のため】お知らせの種類と材料から、画面に出す文を組み立てる。
 * API は文章そのものを返さないので、ここで文にする（文言を変えるときに、バックエンドを直さずに済む）。
 */
function toMessage(notification: Notification): string {
  switch (notification.type) {
    case 'late_cancellation': {
      const who = notification.actor ? `${displayName(notification.actor)} さん` : '参加者'
      return `「${notification.eventTitle}」で、${who}が締め切り後にキャンセルしました。`
    }
    case 'event_deleted':
      return `申し込んでいた「${notification.eventTitle}」は、削除されました。`
  }
}

/**
 * 【何のため】お知らせ一覧の画面を表示する。
 */
function NotificationListPage() {
  const navigate = useNavigate()
  const { refreshUnreadCount } = useLayoutContext()

  // これまでに読み込んだお知らせ（ページごとに読み込んだものを、つなげて持つ）
  const [items, setItems] = useState<Notification[]>([])
  // 次に読み込むページ番号
  const [nextPage, setNextPage] = useState(1)
  // まだ読み込んでいないお知らせがあるか
  const [hasMore, setHasMore] = useState(true)
  // 未読の件数（全部の中で）
  const [unreadCount, setUnreadCount] = useState(0)
  // 読み込みの途中かどうか
  const [isLoading, setIsLoading] = useState(false)
  // 最初の読み込みが終わったかどうか（「お知らせはまだありません」を出すかどうかに使う）
  const [isFirstLoaded, setIsFirstLoaded] = useState(false)
  const [loadError, setLoadError] = useState<unknown>(undefined)
  const [actionError, setActionError] = useState<string | null>(null)

  // 読み込みの途中かどうかを、すぐに確かめるための入れ物。
  // state は画面を描き直すまで新しい値にならないので、同時に2回読み込みが始まらないように、こちらで確かめる
  const loadingRef = useRef(false)

  /**
   * 【何のため】次の 30件を読み込んで、今の一覧の後ろに足す。
   * useCallback は、関数を作り直さずに使い回すための書き方（下の useEffect で使うため）。
   */
  const loadMore = useCallback(async () => {
    if (loadingRef.current || !hasMore) {
      return
    }
    loadingRef.current = true
    setIsLoading(true)
    setLoadError(undefined)
    try {
      const result = await fetchNotifications(nextPage)
      // 前の一覧の後ろに、新しく読み込んだ分を足す
      setItems((previous) => [...previous, ...result.items])
      setUnreadCount(result.unreadCount)
      setNextPage(result.page + 1)
      setHasMore(result.page < result.totalPages)
      setIsFirstLoaded(true)
    } catch (error) {
      setLoadError(error)
    } finally {
      loadingRef.current = false
      setIsLoading(false)
    }
  }, [hasMore, nextPage])

  // 一覧の一番下に置く目印。これが画面に見えたら、次の 30件を読み込む
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 目印が見えたら、次の 30件を読み込む。
  // IntersectionObserver は、ブラウザに備わっている「要素が画面に見えたかどうか」を知らせてくれる道具。
  // 画面を開いた直後は一覧が空なので、目印がすぐに見え、最初の 30件もこの仕組みで読み込まれる。
  // 開発中は React の確認の仕組み（StrictMode）で2回準備されるが、loadingRef があるので二重には読み込まない
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || !hasMore || loadError !== undefined) {
      return
    }
    const observer = new IntersectionObserver((entries) => {
      // entries[0].isIntersecting は「目印が画面に見えている」という意味
      if (entries[0].isIntersecting) {
        void loadMore()
      }
    })
    observer.observe(sentinel)
    // 画面を離れるときや、条件が変わったときに、見張りをやめる
    return () => observer.disconnect()
  }, [loadMore, hasMore, loadError])

  /**
   * 【何のため】お知らせを開いたときの処理。既読にして、イベントがあればイベントの詳細へ移動する。
   * イベントが削除されたお知らせは、移動先がないので、既読にするだけ。
   * 既読にしたお知らせは、一覧から消さずに、表示だけを既読に変える。
   */
  async function open(notification: Notification) {
    setActionError(null)
    try {
      if (!notification.read) {
        await markAsRead(notification.id)
        setItems((previous) => previous.map((n) => (n.id === notification.id ? { ...n, read: true } : n)))
        setUnreadCount((count) => Math.max(0, count - 1))
        refreshUnreadCount()
      }
      if (notification.eventId !== null) {
        navigate(`/communities/${notification.communityId}/events/${notification.eventId}`)
      }
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : '通信中にエラーが発生しました。')
    }
  }

  /**
   * 【何のため】すべて既読にする。読み込み済みのお知らせの表示も、すべて既読に変える。
   */
  async function readAll() {
    setActionError(null)
    try {
      await markAllAsRead()
      setItems((previous) => previous.map((n) => ({ ...n, read: true })))
      setUnreadCount(0)
      refreshUnreadCount()
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : '通信中にエラーが発生しました。')
    }
  }

  return (
    <section>
      <div className={styles.header}>
        <h1>お知らせ</h1>
        {unreadCount > 0 && (
          <button type="button" className={styles.button} onClick={readAll}>
            すべて既読にする
          </button>
        )}
      </div>

      {actionError && (
        <p className={formStyles.formError} role="alert">
          {actionError}
        </p>
      )}
      {isFirstLoaded && <p className={styles.message}>未読 {unreadCount}件</p>}
      {isFirstLoaded && items.length === 0 && <p className={styles.message}>お知らせはまだありません。</p>}

      <ul className={styles.list}>
        {items.map((notification) => (
          <li key={notification.id} className={styles.card}>
            {/* 未読のお知らせは太字にし、「未読」の札を付ける */}
            <p style={{ margin: 0, fontWeight: notification.read ? 'normal' : 'bold' }}>
              {!notification.read && (
                <span className={`${styles.badge} ${styles.badgeAccent}`} style={{ marginLeft: 0, marginRight: 8 }}>
                  未読
                </span>
              )}
              {toMessage(notification)}
            </p>
            <p className={styles.cardMeta}>{formatDateTime(notification.createdAt)}</p>
            <div className={styles.actions}>
              {notification.eventId !== null ? (
                <button type="button" className={styles.button} onClick={() => void open(notification)}>
                  イベントを見る
                </button>
              ) : (
                !notification.read && (
                  <button type="button" className={styles.button} onClick={() => void open(notification)}>
                    既読にする
                  </button>
                )
              )}
            </div>
          </li>
        ))}
      </ul>

      {isLoading && <p className={styles.message}>読み込み中…</p>}
      {loadError !== undefined && <LoadError error={loadError} />}

      {/* 一覧の一番下の目印。これが画面に見えたら、次の 30件を読み込む */}
      <div ref={sentinelRef} />

      {/* スクロールで読み込めないとき（読み込みに失敗したときなど）のためのボタン */}
      {hasMore && isFirstLoaded && !isLoading && (
        <div className={styles.actions}>
          <button type="button" className={styles.button} onClick={() => void loadMore()}>
            さらに読み込む
          </button>
        </div>
      )}
      {!hasMore && items.length > 0 && <p className={styles.message}>すべてのお知らせを表示しました（{items.length}件）。</p>}
    </section>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default NotificationListPage

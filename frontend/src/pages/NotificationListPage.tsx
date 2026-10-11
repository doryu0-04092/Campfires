/**
 * 【役割】S-15「お知らせ一覧」の画面。URL は /notifications
 * 【なぜ必要か】締め切り後のキャンセルや、イベントの削除のお知らせを確認するため（F-20、要件定義書 5.10）。
 * - 新しい順に、最初に 30件を表示し、下までスクロールすると次の 30件を足して表示する（共通の仕組み useInfiniteList）
 * - 既読になったお知らせも、消さずに残す
 * - お知らせを開くと既読になり、イベントがあればイベントの詳細へ移動する
 * 既読にしたら、画面上部の未読の件数も取り直してもらう。
 */

import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { fetchNotifications, markAllAsRead, markAsRead } from '../api/notifications'
import formStyles from '../components/Form.module.css'
import InfiniteListFooter from '../components/InfiniteListFooter'
import styles from '../components/List.module.css'
import { useLayoutContext } from '../components/layoutContext'
import type { Notification, NotificationListResponse } from '../types/notification'
import { displayName } from '../types/user'
import { formatDateTime } from '../utils/formatDateTime'
import { useInfiniteList } from '../utils/useInfiniteList'

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
  const fetchPage = useCallback((page: number) => fetchNotifications(page), [])
  const list = useInfiniteList<Notification, NotificationListResponse>('notifications', fetchPage)

  // この画面の中で既読にしたお知らせの番号。未読の件数を、読み込み直さずに減らすために使う
  const [readIds, setReadIds] = useState<Set<number>>(new Set())
  // 「すべて既読にする」を押したかどうか
  const [allRead, setAllRead] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  // 未読の件数 = 最初に届いた未読の件数 - この画面で既読にした数
  const unreadCount = allRead ? 0 : Math.max(0, (list.last?.unreadCount ?? 0) - readIds.size)

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
        list.updateItems((items) => items.map((n) => (n.id === notification.id ? { ...n, read: true } : n)))
        // new Set([...readIds, 番号]) は「今の集まりに、番号を1つ足した新しい集まり」を作る書き方
        setReadIds((ids) => new Set([...ids, notification.id]))
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
      list.updateItems((items) => items.map((n) => ({ ...n, read: true })))
      setAllRead(true)
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
      {list.isFirstLoaded && <p className={styles.message}>未読 {unreadCount}件</p>}
      {list.isFirstLoaded && list.items.length === 0 && <p className={styles.message}>お知らせはまだありません。</p>}

      <ul className={styles.list}>
        {list.items.map((notification) => (
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

      <InfiniteListFooter list={list} sentinelRef={list.sentinelRef} />
      {!list.hasMore && list.items.length > 0 && (
        <p className={styles.message}>すべてのお知らせを表示しました（{list.items.length}件）。</p>
      )}
    </section>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default NotificationListPage

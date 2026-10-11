/**
 * 【役割】S-15「お知らせ一覧」の画面。URL は /notifications
 * 【なぜ必要か】締め切り後のキャンセルや、イベントの削除のお知らせを確認するため（F-20、要件定義書 5.10）。
 * お知らせを開くと既読になり、イベントがあればイベントの詳細へ移動する。
 * 既読にしたら、画面上部の未読の件数も取り直してもらう。
 */

import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { fetchNotifications, markAllAsRead, markAsRead } from '../api/notifications'
import formStyles from '../components/Form.module.css'
import styles from '../components/List.module.css'
import LoadError from '../components/LoadError'
import Pagination from '../components/Pagination'
import { useLayoutContext } from '../components/layoutContext'
import type { Notification } from '../types/notification'
import { displayName } from '../types/user'
import { formatDateTime } from '../utils/formatDateTime'
import { pageHref, parsePage } from '../utils/page'
import { useLoader } from '../utils/useLoader'

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
  const [searchParams] = useSearchParams()
  const page = parsePage(searchParams.get('page'))
  const navigate = useNavigate()
  const { refreshUnreadCount } = useLayoutContext()
  const { isLoading, data, error, reload } = useLoader(`notifications:${page}`, (signal) => fetchNotifications(page, signal))
  const [actionError, setActionError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  /**
   * 【何のため】お知らせを開いたときの処理。既読にして、イベントがあればイベントの詳細へ移動する。
   * イベントが削除されたお知らせは、移動先がないので、既読にして一覧を新しくするだけ。
   */
  async function open(notification: Notification) {
    setActionError(null)
    try {
      if (!notification.read) {
        await markAsRead(notification.id)
        refreshUnreadCount()
      }
      if (notification.eventId !== null) {
        navigate(`/communities/${notification.communityId}/events/${notification.eventId}`)
      } else {
        reload()
      }
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : '通信中にエラーが発生しました。')
    }
  }

  /**
   * 【何のため】すべて既読にする。
   */
  async function readAll() {
    setIsSubmitting(true)
    setActionError(null)
    try {
      await markAllAsRead()
      refreshUnreadCount()
      reload()
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : '通信中にエラーが発生しました。')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section>
      <div className={styles.header}>
        <h1>お知らせ</h1>
        {data && data.unreadCount > 0 && (
          <button type="button" className={styles.button} onClick={readAll} disabled={isSubmitting}>
            すべて既読にする
          </button>
        )}
      </div>

      {actionError && (
        <p className={formStyles.formError} role="alert">
          {actionError}
        </p>
      )}
      {isLoading && <p className={styles.message}>読み込み中…</p>}
      {error !== undefined && <LoadError error={error} />}

      {data && (
        <>
          <p className={styles.message}>未読 {data.unreadCount}件</p>
          {data.items.length === 0 ? (
            <p className={styles.message}>お知らせはまだありません。</p>
          ) : (
            <ul className={styles.list}>
              {data.items.map((notification) => (
                <li key={notification.id} className={styles.card}>
                  {/* 未読のお知らせは太字にし、「未読」の札を付ける */}
                  <p style={{ margin: 0, fontWeight: notification.read ? 'normal' : 'bold' }}>
                    {!notification.read && <span className={`${styles.badge} ${styles.badgeAccent}`} style={{ marginLeft: 0, marginRight: 8 }}>未読</span>}
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
          )}
          <Pagination page={data.page} totalPages={data.totalPages} buildHref={(p) => pageHref('/notifications', p)} />
        </>
      )}
    </section>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default NotificationListPage

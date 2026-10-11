/**
 * 【役割】S-19「イベントを選ぶ」の画面。URL は /manage/select/event-edit、/manage/select/applicants
 * 【なぜ必要か】左のメニューの「イベント編集」「申込者一覧」は、どのイベントの画面かを決めないと開けない。
 * メニューを押したあとに、この画面で、管理しているコミュニティのイベントから選ぶ（Issue #45）。
 * - イベント編集：開催前のイベントだけ（終了したイベントは編集できない）
 * - 申込者一覧：開催前と終了済みを切り替えて選べる。承認待ちの人数も出す
 */

import { Link, useSearchParams } from 'react-router'
import { fetchManagedEvents } from '../api/events'
import styles from '../components/List.module.css'
import LoadError from '../components/LoadError'
import Pagination from '../components/Pagination'
import { ROLE_LABELS } from '../types/community'
import { formatDateTime } from '../utils/formatDateTime'
import { parsePage } from '../utils/page'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】この画面で何をするか。
 */
export type SelectEventPurpose = 'event-edit' | 'applicants'

/**
 * 【何のため】イベントを選ぶ画面を表示する。
 */
function SelectEventPage({ purpose }: { purpose: SelectEventPurpose }) {
  const [searchParams] = useSearchParams()
  const page = parsePage(searchParams.get('page'))
  // 終了済みを見られるのは、申込者一覧のときだけ
  const status = purpose === 'applicants' && searchParams.get('status') === 'past' ? 'past' : 'upcoming'
  const basePath = `/manage/select/${purpose}`
  const { isLoading, data, error } = useLoader(`managed-events:${status}:${page}`, (signal) =>
    fetchManagedEvents(status, page, signal),
  )

  /**
   * 【何のため】ページ番号と、開催前・終了済みを入れた URL を作る。
   */
  function href(nextStatus: 'upcoming' | 'past', nextPage: number) {
    const params = new URLSearchParams()
    if (nextStatus === 'past') {
      params.set('status', 'past')
    }
    if (nextPage > 1) {
      params.set('page', String(nextPage))
    }
    const query = params.toString()
    return query ? `${basePath}?${query}` : basePath
  }

  return (
    <section>
      <h1>{purpose === 'event-edit' ? 'イベント編集：イベントを選ぶ' : '申込者一覧：イベントを選ぶ'}</h1>
      <p className={styles.message}>オーナーか運営スタッフをしているコミュニティのイベントから選びます。</p>

      {/* 申込者一覧のときだけ、開催前と終了済みを切り替えられる */}
      {purpose === 'applicants' && (
        <div className={styles.actions}>
          <Link to={href('upcoming', 1)} className={`${styles.button} ${status === 'upcoming' ? styles.buttonPrimary : ''}`}>
            開催前
          </Link>
          <Link to={href('past', 1)} className={`${styles.button} ${status === 'past' ? styles.buttonPrimary : ''}`}>
            終了済み
          </Link>
        </div>
      )}

      {isLoading && <p className={styles.message}>読み込み中…</p>}
      {error !== undefined && <LoadError error={error} />}
      {data && (
        <>
          {data.items.length === 0 ? (
            <p className={styles.message}>
              {status === 'past' ? '終了したイベントはありません。' : '開催前のイベントはありません。'}
            </p>
          ) : (
            <ul className={styles.list}>
              {data.items.map((event) => {
                const base = `/manage/communities/${event.communityId}/events/${event.id}`
                return (
                  <li key={event.id} className={styles.card}>
                    <h2 className={styles.cardTitle}>
                      {/* 押すと、選んだイベントの編集・申込者一覧へ移動する */}
                      <Link to={purpose === 'event-edit' ? `${base}/edit` : `${base}/applicants`}>{event.title}</Link>
                      {event.pendingCount > 0 && (
                        <span className={`${styles.badge} ${styles.badgeAccent}`}>承認待ち {event.pendingCount}人</span>
                      )}
                    </h2>
                    <p className={styles.cardMeta}>
                      {event.communityName}（{ROLE_LABELS[event.myRole]}）／ 開催日時：{formatDateTime(event.startAt)} ／
                      申込み {event.capacity - event.remainingSeats}人 / 定員 {event.capacity}人
                    </p>
                  </li>
                )
              })}
            </ul>
          )}
          <Pagination page={data.page} totalPages={data.totalPages} buildHref={(p) => href(status, p)} />
        </>
      )}
    </section>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default SelectEventPage

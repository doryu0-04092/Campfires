/**
 * 【役割】S-11「申込者一覧」の画面。URL は /manage/communities/:communityId/events/:eventId/applicants
 * 【なぜ必要か】オーナーと運営スタッフが、イベントの申込者を確認するため（F-13）。
 * 表示するのはニックネームと申込み日時だけで、本名は表示しない（要件定義書 5.6）。
 * アカウントを削除した人は「退会したユーザー」と表示する。
 */

import { Link, useParams, useSearchParams } from 'react-router'
import { fetchApplications, fetchEvent } from '../api/events'
import formStyles from '../components/Form.module.css'
import styles from '../components/List.module.css'
import LoadError from '../components/LoadError'
import Pagination from '../components/Pagination'
import { displayName } from '../types/user'
import { formatDateTime } from '../utils/formatDateTime'
import { pageHref, parsePage } from '../utils/page'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】申込者一覧の画面を表示する。
 */
function ApplicantListPage() {
  const params = useParams()
  const communityId = Number(params.communityId)
  const eventId = Number(params.eventId)
  const [searchParams] = useSearchParams()
  const page = parsePage(searchParams.get('page'))

  // イベントの情報（タイトル、定員）と、申込者の一覧を読み込む
  const event = useLoader(`event:${communityId}:${eventId}`, (signal) => fetchEvent(communityId, eventId, signal))
  const applicants = useLoader(`applicants:${communityId}:${eventId}:${page}`, (signal) =>
    fetchApplications(communityId, eventId, page, signal),
  )

  if (event.isLoading) {
    return <p>読み込み中…</p>
  }
  if (event.error !== undefined || !event.data) {
    return <LoadError error={event.error} />
  }
  if (event.data.myRole !== 'owner' && event.data.myRole !== 'staff') {
    return <p className={formStyles.formError}>申込者一覧は、オーナーと運営スタッフだけが見られます。</p>
  }
  const applied = event.data.capacity - event.data.remainingSeats

  return (
    <section>
      <h1>申込者一覧：{event.data.title}</h1>
      <p>
        <Link to={`/communities/${communityId}/events/${eventId}`}>イベントの詳細へ戻る</Link>
      </p>
      <p className={styles.message}>
        申込み済み {applied}人 / 定員 {event.data.capacity}人、キャンセル待ち {event.data.waitlistCount}人
      </p>

      {applicants.isLoading && <p className={styles.message}>読み込み中…</p>}
      {applicants.error !== undefined && <LoadError error={applicants.error} />}
      {applicants.data && (
        <>
          {applicants.data.items.length === 0 ? (
            <p className={styles.message}>まだ申込みはありません。</p>
          ) : (
            <ul className={styles.list}>
              {applicants.data.items.map((application) => (
                <li key={`${application.user.userId}-${application.appliedAt}`} className={styles.card}>
                  <strong>{displayName(application.user)}</strong>
                  <span className={`${styles.badge} ${application.status === 'waitlisted' ? styles.badgeMuted : ''}`}>
                    {application.status === 'applied' ? '申込み済み' : `キャンセル待ち ${application.waitlistPosition}番目`}
                  </span>
                  <span className={styles.cardMeta}>（申込み日時：{formatDateTime(application.appliedAt)}）</span>
                </li>
              ))}
            </ul>
          )}
          <Pagination
            page={applicants.data.page}
            totalPages={applicants.data.totalPages}
            buildHref={(p) => pageHref(`/manage/communities/${communityId}/events/${eventId}/applicants`, p)}
          />
        </>
      )}
    </section>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default ApplicantListPage

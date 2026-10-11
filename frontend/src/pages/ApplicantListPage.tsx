/**
 * 【役割】S-11「申込者一覧」の画面。URL は /manage/communities/:communityId/events/:eventId/applicants
 * 【なぜ必要か】オーナーと運営スタッフが、イベントの申込者を確認し、承認待ちの申込みを承認・却下するため（F-13、要件定義書 5.12）。
 * 表示するのはニックネームと申込み日時だけで、本名は表示しない（要件定義書 5.6）。
 * アカウントを削除した人は「退会したユーザー」と表示する。
 * 並び順：承認待ち（申し込んだ順）→ 申込み済み → キャンセル待ち（順番どおり）。
 */

import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { decideApplication, fetchApplications, fetchEvent } from '../api/events'
import formStyles from '../components/Form.module.css'
import styles from '../components/List.module.css'
import LoadError from '../components/LoadError'
import Pagination from '../components/Pagination'
import type { Application } from '../types/event'
import { displayName } from '../types/user'
import { formatDateTime } from '../utils/formatDateTime'
import { pageHref, parsePage } from '../utils/page'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】申込みの状況を、札に出す文にする。
 */
function statusLabel(application: Application): string {
  switch (application.status) {
    case 'pending':
      return '承認待ち'
    case 'applied':
      return '申込み済み'
    case 'waitlisted':
      return `キャンセル待ち ${application.waitlistPosition}番目`
    case 'rejected':
      return '却下'
  }
}

/**
 * 【何のため】申込者一覧の画面を表示する。
 */
function ApplicantListPage() {
  const params = useParams()
  const communityId = Number(params.communityId)
  const eventId = Number(params.eventId)
  const [searchParams] = useSearchParams()
  const page = parsePage(searchParams.get('page'))
  // 承認・却下したときのエラーの文
  const [actionError, setActionError] = useState<string | null>(null)
  // 承認・却下の処理中の人の番号（ボタンを押せないようにする）
  const [busyUserId, setBusyUserId] = useState<number | null>(null)

  // イベントの情報（タイトル、定員）と、申込者の一覧を読み込む
  const event = useLoader(`event:${communityId}:${eventId}`, (signal) => fetchEvent(communityId, eventId, signal))
  const applicants = useLoader(`applicants:${communityId}:${eventId}:${page}`, (signal) =>
    fetchApplications(communityId, eventId, page, signal),
  )

  /**
   * 【何のため】承認待ちの申込みを、承認・却下する。終わったら、人数と一覧を読み込み直す。
   */
  async function decide(application: Application, status: 'approved' | 'rejected') {
    if (status === 'rejected') {
      const ok = window.confirm(
        `${displayName(application.user)} さんの申込みを却下しますか？\n却下した人は、このイベントに申し込み直せません。`,
      )
      if (!ok) {
        return
      }
    }
    setBusyUserId(application.user.userId)
    setActionError(null)
    try {
      await decideApplication(communityId, eventId, application.user.userId, status)
      event.reload()
      applicants.reload()
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : '通信中にエラーが発生しました。時間をおいて、もう一度お試しください。')
    } finally {
      setBusyUserId(null)
    }
  }

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
  // 承認・却下は、イベントの開始日時まで（要件定義書 5.12）
  const canDecide = new Date(event.data.startAt) > new Date()

  return (
    <section>
      <h1>申込者一覧：{event.data.title}</h1>
      <p>
        <Link to={`/communities/${communityId}/events/${eventId}`}>イベントの詳細へ戻る</Link>
      </p>
      <p className={styles.message}>
        申込み済み {applied}人 / 定員 {event.data.capacity}人、キャンセル待ち {event.data.waitlistCount}人、承認待ち{' '}
        {event.data.pendingCount}人
      </p>
      {event.data.pendingCount > 0 && (
        <p className={styles.message}>
          承認すると席が確保されます（その時点で満席なら、キャンセル待ちになります）。承認・却下できるのは、イベントの開始日時までです。
        </p>
      )}
      {actionError && (
        <p className={formStyles.formError} role="alert">
          {actionError}
        </p>
      )}

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
                  <span
                    className={`${styles.badge} ${application.status === 'waitlisted' ? styles.badgeMuted : ''} ${application.status === 'pending' ? styles.badgeAccent : ''}`}
                  >
                    {statusLabel(application)}
                  </span>
                  <span className={styles.cardMeta}>（申込み日時：{formatDateTime(application.appliedAt)}）</span>
                  {/* 承認待ちの人には、承認・却下のボタンを出す */}
                  {application.status === 'pending' && canDecide && (
                    <div className={styles.actions}>
                      <button
                        type="button"
                        className={`${styles.button} ${styles.buttonPrimary}`}
                        onClick={() => void decide(application, 'approved')}
                        disabled={busyUserId !== null}
                      >
                        承認する
                      </button>
                      <button
                        type="button"
                        className={styles.button}
                        onClick={() => void decide(application, 'rejected')}
                        disabled={busyUserId !== null}
                      >
                        却下する
                      </button>
                    </div>
                  )}
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

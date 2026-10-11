/**
 * 【役割】S-10「イベント編集」の画面。URL は /manage/communities/:communityId/events/:eventId/edit
 * 【なぜ必要か】イベントの内容を変え、削除するため（F-10）。オーナーと運営スタッフが使える。
 * 終了したイベントは、編集も削除もできない（参加の記録として残すため）。
 */

import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { deleteEvent, fetchEvent, updateEvent } from '../api/events'
import EventForm from '../components/EventForm'
import formStyles from '../components/Form.module.css'
import LoadError from '../components/LoadError'
import type { EventDetail } from '../types/event'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】イベント編集の画面を表示する。
 */
function EventEditPage() {
  const params = useParams()
  const communityId = Number(params.communityId)
  const eventId = Number(params.eventId)
  const navigate = useNavigate()
  const { isLoading, data, error } = useLoader(`event:${communityId}:${eventId}`, (signal) =>
    fetchEvent(communityId, eventId, signal),
  )

  if (isLoading) {
    return <p>読み込み中…</p>
  }
  if (error !== undefined || !data) {
    return <LoadError error={error} />
  }
  if (data.myRole !== 'owner' && data.myRole !== 'staff') {
    return <p className={formStyles.formError}>イベントを編集できるのは、オーナーと運営スタッフだけです。</p>
  }
  const detailPath = `/communities/${communityId}/events/${eventId}`
  if (new Date(data.endAt) <= new Date()) {
    return (
      <p className={formStyles.formError}>
        終了したイベントは、編集も削除もできません。 <Link to={detailPath}>イベントの詳細へ戻る</Link>
      </p>
    )
  }

  return (
    <section className={formStyles.container}>
      <h1>イベントを編集</h1>
      <p>
        <Link to={detailPath}>イベントの詳細へ戻る</Link>
      </p>
      <EventForm
        initialValue={data}
        submitLabel="保存する"
        onSubmit={async (input) => {
          await updateEvent(communityId, eventId, input)
          // 保存に成功したら、イベントの詳細へ戻る
          navigate(detailPath)
        }}
      />
      <DeleteEventSection event={data} onDeleted={() => navigate(`/communities/${communityId}`)} />
    </section>
  )
}

/**
 * 【何のため】イベントを削除するボタンを表示する。
 * 申込者がいる場合は、申込みも削除され、申込者にお知らせが届くことを伝えてから確認する（要件定義書 5.2）。
 */
function DeleteEventSection({ event, onDeleted }: { event: EventDetail; onDeleted: () => void }) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // 申込みの人数（申込み済み ＋ キャンセル待ち ＋ 承認待ち）
  const applicantCount = event.capacity - event.remainingSeats + event.waitlistCount + event.pendingCount

  async function handleDelete() {
    const ok = window.confirm(
      [
        `「${event.title}」を削除しますか？`,
        applicantCount > 0
          ? `申込み（${applicantCount}人。キャンセル待ち・承認待ちを含む）も削除され、申し込んでいた人にお知らせが届きます。`
          : '申込みはありません。',
        '削除したイベントは、元に戻せません。',
      ].join('\n'),
    )
    if (!ok) {
      return
    }
    setIsSubmitting(true)
    setError(null)
    try {
      await deleteEvent(event.communityId, event.id)
      onDeleted()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '通信中にエラーが発生しました。')
      setIsSubmitting(false)
    }
  }

  return (
    <div className={`${formStyles.section} ${formStyles.dangerSection}`}>
      <h2>イベントの削除</h2>
      <p>削除すると、申込みも一緒に削除され、申し込んでいた人にお知らせが届きます。</p>
      <button type="button" className={formStyles.danger} onClick={handleDelete} disabled={isSubmitting}>
        このイベントを削除する
      </button>
      {error && (
        <p className={formStyles.formError} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default EventEditPage

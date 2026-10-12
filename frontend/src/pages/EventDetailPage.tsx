/**
 * 【役割】S-09「イベント詳細」の画面。URL は /communities/:communityId/events/:eventId
 * 【なぜ必要か】イベントの内容を見て、申し込む・キャンセルするため（F-11、F-12、F-15）。
 * - 満席のときは、キャンセル待ちとして申し込める
 * - 参加の承認の方法（自動・手動）を表示する。手動なら、申し込むと「承認待ち」になる（要件定義書 5.12）
 * - メンバー以外は、イベントが受け付けているときだけ申し込める
 * - 締め切り後は申し込めない。キャンセルはできるが、「原則としてキャンセルはできません」と確認を出す（要件定義書 5.2）
 * - オーナーと運営スタッフには、編集と申込者一覧へのリンクを出す
 * ボタンを出し分けるのは使いやすさのためで、本当のチェックはバックエンド（偽の API）が行う。
 */

import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { applyToEvent, cancelMyApplication, fetchEvent } from '../api/events'
import { useAuth } from '../auth/useAuth'
import formStyles from '../components/Form.module.css'
import styles from '../components/List.module.css'
import { returnToState } from '../utils/returnTo'
import LoadError from '../components/LoadError'
import { APPROVAL_LABELS, type EventDetail, type MyApplication } from '../types/event'
import { formatDateTime } from '../utils/formatDateTime'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】イベント詳細の画面を表示する。
 */
function EventDetailPage() {
  const params = useParams()
  const communityId = Number(params.communityId)
  const eventId = Number(params.eventId)
  const { state } = useAuth()
  // ログイン状態が変わったら（ログイン・ログアウト）、自分との関係が変わるので読み込み直す
  const { isLoading, data, error, reload } = useLoader(`event:${communityId}:${eventId}:${state.status}`, (signal) =>
    fetchEvent(communityId, eventId, signal),
  )

  if (isLoading) {
    return <p className={styles.message}>読み込み中…</p>
  }
  if (error !== undefined || !data) {
    return <LoadError error={error} />
  }

  const now = new Date()
  const isEnded = new Date(data.endAt) <= now
  const isFull = data.remainingSeats === 0

  return (
    <section>
      <h1>{data.title}</h1>
      <p>
        <Link to={`/communities/${data.communityId}`}>{data.communityName}</Link>
        {data.communityArchivedAt && <span className={`${styles.badge} ${styles.badgeMuted}`}>アーカイブ済み</span>}
      </p>

      {isEnded && <p className={styles.banner}>このイベントは終了しました。</p>}

      {/* イベントの情報。dl・dt・dd は「項目名と値」の組を並べるタグ */}
      <dl className={styles.card}>
        <InfoRow label="開催日時" value={`${formatDateTime(data.startAt)} 〜 ${formatDateTime(data.endAt)}`} />
        <InfoRow label="申込み締め切り" value={formatDateTime(data.applicationDeadline)} />
        <InfoRow label="場所" value={data.location} />
        <InfoRow
          label="定員"
          value={`${data.capacity}人（残り ${data.remainingSeats}人${data.waitlistCount > 0 ? `、キャンセル待ち ${data.waitlistCount}人` : ''}${data.pendingCount > 0 ? `、承認待ち ${data.pendingCount}人` : ''}）`}
        />
        <InfoRow
          label="参加の承認"
          value={`メンバー：${APPROVAL_LABELS[data.memberApproval]}、メンバー以外：${APPROVAL_LABELS[data.nonMemberApproval]}`}
        />
      </dl>

      {/* 説明の中の改行を、そのまま表示する */}
      {data.description && <p style={{ whiteSpace: 'pre-wrap' }}>{data.description}</p>}

      {!isEnded && <ApplicationActions key={data.myApplication?.status ?? 'none'} event={data} isFull={isFull} onChanged={reload} />}
      <ManageLinks event={data} />
    </section>
  )
}

/**
 * 【何のため】イベントの情報1行分（項目名と値）を表示する。
 */
function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', gap: 12, padding: '2px 0' }}>
      <dt style={{ minWidth: '7em', color: 'var(--text-muted)' }}>{label}</dt>
      <dd style={{ margin: 0 }}>{value}</dd>
    </div>
  )
}

/**
 * 【何のため】自分の申込みの状況を、文にする。
 */
function myStatusText(mine: MyApplication): string {
  switch (mine.status) {
    case 'pending':
      return '承認を待っています。オーナーか運営スタッフが承認すると、参加が決まります（承認の時点で満席なら、キャンセル待ちになります）。'
    case 'applied':
      return '申込み済みです。'
    case 'waitlisted':
      return `キャンセル待ちです（${mine.waitlistPosition}番目）。空きが出て、締め切り前なら、自動で繰り上がります。`
    case 'rejected':
      return 'このイベントへの参加は、承認されませんでした。'
  }
}

/**
 * 【何のため】取り消しのボタンの文を、申込みの状況に合わせて決める。
 */
function cancelButtonText(mine: MyApplication): string {
  switch (mine.status) {
    case 'pending':
      return '申込みを取り消す'
    case 'waitlisted':
      return 'キャンセル待ちをやめる'
    default:
      return '申込みをキャンセルする'
  }
}

/**
 * 【何のため】申込みとキャンセルのボタンを表示する。見ている人の状況によって、出すものを変える。
 */
function ApplicationActions({ event, isFull, onChanged }: { event: EventDetail; isFull: boolean; onChanged: () => void }) {
  const { state } = useAuth()
  const location = useLocation()
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isDeadlinePassed = new Date(event.applicationDeadline) <= new Date()
  // 自分に使われる承認の方法（メンバーかどうかで変わる。myRole が null ならメンバーではない）
  const approval = event.myRole !== null ? event.memberApproval : event.nonMemberApproval

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
   * 【何のため】キャンセルのボタンが押されたときの処理。
   * 締め切り後は、原則としてキャンセルできないことを伝えて確認する（要件定義書 5.2）。
   */
  function handleCancel() {
    // 承認待ちは席を使っていないので、締め切り後でも、ふつうの確認だけにする
    const isPending = event.myApplication?.status === 'pending'
    const message = isPending
      ? '承認待ちの申込みを取り消しますか？'
      : isDeadlinePassed
      ? '申込みの締め切りを過ぎています。原則としてキャンセルはできません。本当にキャンセルしますか？'
      : 'このイベントの申込みをキャンセルしますか？'
    if (window.confirm(message)) {
      void run(() => cancelMyApplication(event.communityId, event.id))
    }
  }

  let content
  if (state.status !== 'authenticated' && isDeadlinePassed) {
    content = <p className={styles.message}>申込みの締め切りを過ぎています。</p>
  } else if (state.status !== 'authenticated') {
    // ログインしていない：「申し込む」を押すと、ログインの画面に移る。ログインのあと、この画面に戻ってくる（utils/returnTo.ts）
    content = (
      <>
        <p className={styles.message}>申し込むには、ログインが必要です。ログインのあと、この画面に戻ります。</p>
        <div className={styles.actions}>
          <Link to="/login" state={returnToState(location)} className={`${styles.button} ${styles.buttonPrimary}`}>
            申し込む
          </Link>
          <Link to="/signup" state={returnToState(location)} className={styles.button}>
            アカウント登録
          </Link>
        </div>
      </>
    )
  } else if (event.myApplication?.status === 'rejected') {
    // 却下された：申し込み直せない（要件定義書 5.12）
    content = <p className={styles.message}>このイベントへの参加は、承認されませんでした。</p>
  } else if (event.myApplication) {
    // 申し込んでいる：状況とキャンセルのボタン
    const mine = event.myApplication
    content = (
      <>
        <p>
          {myStatusText(mine)}
          <span className={styles.cardMeta}>（申込み日時：{formatDateTime(mine.appliedAt)}）</span>
        </p>
        <button type="button" className={`${styles.button} ${styles.buttonDanger}`} onClick={handleCancel} disabled={isSubmitting}>
          {cancelButtonText(mine)}
        </button>
      </>
    )
  } else if (isDeadlinePassed) {
    content = <p className={styles.message}>申込みの締め切りを過ぎています。</p>
  } else if (approval === 'closed') {
    // メンバー以外を受け付けていない：コミュニティへの参加を案内する（要件定義書 5.12）
    content = (
      <p className={styles.message}>
        このイベントに申し込むには、<Link to={`/communities/${event.communityId}`}>コミュニティ</Link>に参加してください。
      </p>
    )
  } else {
    // まだ申し込んでいない：申し込める。自動なら満席でキャンセル待ち、手動なら承認待ちになる
    content = (
      <>
        {approval === 'manual' && (
          <p className={styles.message}>申し込むと、オーナーか運営スタッフの承認を待ちます。席は、承認されたときに確保されます。</p>
        )}
        {approval === 'auto' && isFull && <p className={styles.message}>満席です。キャンセル待ちとして申し込めます。</p>}
        <button
          type="button"
          className={`${styles.button} ${styles.buttonPrimary}`}
          onClick={() => void run(() => applyToEvent(event.communityId, event.id))}
          disabled={isSubmitting}
        >
          {approval === 'auto' && isFull ? 'キャンセル待ちに申し込む' : '申し込む'}
        </button>
      </>
    )
  }

  return (
    <div className={formStyles.section}>
      <h2>申込み</h2>
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
 * 【何のため】オーナーと運営スタッフに、イベントの管理の画面へのリンクを表示する。
 */
function ManageLinks({ event }: { event: EventDetail }) {
  if (event.myRole !== 'owner' && event.myRole !== 'staff') {
    return null
  }
  const base = `/manage/communities/${event.communityId}/events/${event.id}`
  const isEnded = new Date(event.endAt) <= new Date()
  return (
    <div className={styles.actions}>
      <span className={styles.cardMeta}>管理：</span>
      {/* 終了したイベントは編集できないので、リンクを出さない */}
      {!isEnded && <Link to={`${base}/edit`} className={styles.button}>イベントを編集</Link>}
      <Link to={`${base}/applicants`} className={styles.button}>申込者一覧</Link>
    </div>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default EventDetailPage

/**
 * 【役割】イベント1件分の情報（タイトル、コミュニティ、開催日時、残りの枠数）を表示する部品。
 * 【なぜ必要か】イベントの一覧は、トップの画面のほか、コミュニティ詳細などでも使う予定のため、
 * 1件分の表示を部品として分けて使い回す。
 */

import { Link } from 'react-router'
import type { EventSummary } from '../types/event'
import { formatDateTime } from '../utils/formatDateTime'
import styles from './EventCard.module.css'

/**
 * 【何のため】この部品が受け取る値の形を決める。
 */
type EventCardProps = {
  // 表示するイベント
  event: EventSummary
}

/**
 * 【何のため】イベント1件分の情報を、カードの形で表示する。
 */
function EventCard({ event }: EventCardProps) {
  // 残りの枠数が 0 なら満席
  const isFull = event.remainingSeats === 0

  return (
    // article：それだけで意味がまとまった内容（ここではイベント1件）を表すタグ
    <article className={styles.card}>
      {/* タイトル：押すとイベント詳細（S-09）へ移動する */}
      <h2 className={styles.title}>
        <Link to={`/communities/${event.communityId}/events/${event.id}`}>{event.title}</Link>
      </h2>

      {/* コミュニティの名前：押すとコミュニティ詳細（S-05）へ移動する */}
      <p className={styles.community}>
        <Link to={`/communities/${event.communityId}`}>{event.communityName}</Link>
      </p>

      {/* dl・dt・dd：「項目名と値」の組を並べるためのタグ（dt が項目名、dd が値） */}
      <dl className={styles.details}>
        <dt>開催日時</dt>
        <dd>{formatDateTime(event.startAt)}</dd>
        <dt>残りの枠</dt>
        {/* 満席なら「満席」、そうでなければ「残り ○ / 定員 ○」と表示する */}
        <dd className={isFull ? styles.full : undefined}>
          {isFull ? '満席' : `${event.remainingSeats} / ${event.capacity}`}
        </dd>
      </dl>
    </article>
  )
}

// イベント一覧の画面などから読み込めるようにする
export default EventCard

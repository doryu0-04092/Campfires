/**
 * 【役割】偽のデータベースの「イベント」と「申込み」の表と、最初に入れておくデータ。
 * 【なぜ必要か】イベントの一覧・検索や、申込みの画面を試せるようにするため。
 * 検索やページの切り替えを試せるように、70件ほどのイベントを自動で作る。
 */

import type { MockCommunity } from './communities'
import { FIRST_DUMMY_USER_ID } from './users'

/**
 * 【何のため】偽のデータベースのイベント1件分の形。
 * 画面に返す形（EventSummary、EventDetail）より項目が多い。本物のデータベースのテーブルに近い形にしている。
 */
export type MockEvent = {
  id: number
  communityId: number
  title: string
  // 説明（検索の対象になる）
  description: string
  // 開始日時、終了日時、申込み締め切り日時（世界標準時の ISO 8601 形式）
  startAt: string
  endAt: string
  applicationDeadline: string
  location: string
  capacity: number
  // 参加の承認の方法（要件定義書 5.12）。メンバー：auto（自動）か manual（手動）
  memberApproval: 'auto' | 'manual'
  // メンバー以外：auto（自動）、manual（手動）、closed（受け付けない）
  nonMemberApproval: 'auto' | 'manual' | 'closed'
  // イベントが作られた日時（一覧の並び順に使う）
  createdAt: string
}

/**
 * 【何のため】偽のデータベースの申込み1件分の形。
 */
export type MockApplication = {
  id: number
  eventId: number
  userId: number
  // pending：承認待ち、applied：申込み済み、waitlisted：キャンセル待ち、rejected：却下
  status: 'pending' | 'applied' | 'waitlisted' | 'rejected'
  appliedAt: string
  // 席の順番を取った日時（キャンセル待ちの順番と、繰り上げの順番に使う）。
  // 自動なら申し込んだ日時、手動なら承認された日時。承認待ち・却下のときは null
  queuedAt: string | null
}

// イベントのタイトルに使う「話題」と「形式」。組み合わせて、いろいろなタイトルを作る
const topics = ['Java', 'Spring Boot', 'React', 'TypeScript', 'SQL', 'AWS', 'Docker', 'Git', 'アルゴリズム', 'テスト']
const formats = ['初心者向け勉強会', 'もくもく会', 'ハンズオン', 'LT会', '読書会', '質問会', 'ペアプロ会']

// 説明に使う文。検索で「週末」「オンライン」などの単語を試せるように、いくつかの種類を混ぜる
const descriptions = [
  '週末の午後に、みんなで手を動かしながら学びます。初心者の方も歓迎です。',
  '平日の夜にオンラインで開催します。質問はチャットでどうぞ。',
  '会場で集まって開催します。ノートパソコンを持ってきてください。',
  '実務で使っている人の話を聞いて、疑問を解消する会です。',
  '本を1章ずつ読み進めます。読んでいなくても参加できます。',
]

// 場所に使う文。個人の住所は書かない（要件定義書 5.2）
const locations = ['オンライン（参加者に URL をお知らせします）', '渋谷区の貸し会議室', '新宿区のコワーキングスペース']

// 1時間と1日を、ミリ秒（1000分の1秒）で表したもの。日時の計算に使う
const HOUR = 60 * 60 * 1000
const DAY = 24 * HOUR

// 作るイベントの数
const EVENT_COUNT = 70

/**
 * 【何のため】イベントの表の最初のデータを、決まったルールで作る。
 * 毎回同じデータになるように、乱数は使わず、番号から計算して作る。
 * 日時は「今」を基準にするので、いつ開いても「開催前」と「終了済み」のイベントが混ざる。
 */
export function createEvents(communities: MockCommunity[], now: number): MockEvent[] {
  const events: MockEvent[] = []

  for (let i = 0; i < EVENT_COUNT; i++) {
    // % は「割った余り」。余りを使うと、一覧の中を順番にぐるぐる選べる
    const topic = topics[i % topics.length]
    const format = formats[i % formats.length]
    const community = communities[i % communities.length]

    // 開始日時：8件に1件は過去（終了済み）、それ以外は1〜60日後のどこか
    const isPast = i % 8 === 0
    const startAt = isPast ? now - (i + 1) * DAY : now + ((i % 60) + 1) * DAY

    events.push({
      id: i + 1,
      communityId: community.id,
      title: `${topic} ${format}`,
      description: descriptions[i % descriptions.length],
      startAt: new Date(startAt).toISOString(),
      // 終了日時：開始の2時間後
      endAt: new Date(startAt + 2 * HOUR).toISOString(),
      // 申込み締め切り日時：開始の1日前
      applicationDeadline: new Date(startAt - DAY).toISOString(),
      location: locations[i % locations.length],
      // 定員：10〜40人
      capacity: 10 + (i % 4) * 10,
      // 承認の方法：20件に1件はメンバーもメンバー以外も手動、別の20件に1件はメンバー以外も自動。
      // それ以外は最初の値（メンバーは自動、メンバー以外は受け付けない）。本物の API の開発用データと同じ
      memberApproval: i % 20 === 10 ? 'manual' : 'auto',
      nonMemberApproval: i % 20 === 10 ? 'manual' : i % 20 === 15 ? 'auto' : 'closed',
      // 作成日時：番号が大きいほど新しい（i 時間前に作られたことにする）
      createdAt: new Date(now - (EVENT_COUNT - i) * HOUR).toISOString(),
    })
  }

  return events
}

/**
 * 【何のため】申込みの表の最初のデータを作る。
 * 5件に1件のイベントは満席にし、それ以外は定員の範囲で、参加者01から順に申し込ませる。
 */
export function createApplications(events: MockEvent[]): MockApplication[] {
  const applications: MockApplication[] = []

  // events.forEach は、一覧の1つずつに対して処理をくり返す書き方
  events.forEach((event, i) => {
    const appliedCount = i % 5 === 0 ? event.capacity : (i * 3) % event.capacity
    // 申込みは、イベントが作られた10分後に行われたことにする。
    // 申込みの日時は必ず「今」より前にする。未来の日時にすると、今申し込んだ人より後ろの順番になり、
    // キャンセル待ちの順番（申し込んだ順）がおかしくなるため
    const appliedTime = new Date(event.createdAt).getTime() + 10 * 60 * 1000
    const appliedAt = new Date(appliedTime).toISOString()

    for (let n = 0; n < appliedCount; n++) {
      applications.push({
        // 申込みの番号は、作った順に 1, 2, 3... とする
        id: applications.length + 1,
        eventId: event.id,
        userId: FIRST_DUMMY_USER_ID + n,
        status: 'applied',
        appliedAt,
        queuedAt: appliedAt,
      })
    }

    // 満席で、定員が 30人以下のイベントには、キャンセル待ちを2人入れる（キャンセル待ちと繰り上げを試すため）
    if (appliedCount === event.capacity && event.capacity <= 30) {
      // キャンセル待ちの人は、満席になったあと（1分後、2分後）に申し込んだことにする
      for (let n = 0; n < 2; n++) {
        applications.push({
          id: applications.length + 1,
          eventId: event.id,
          userId: FIRST_DUMMY_USER_ID + event.capacity + n,
          status: 'waitlisted',
          appliedAt: new Date(appliedTime + (n + 1) * 60 * 1000).toISOString(),
          queuedAt: new Date(appliedTime + (n + 1) * 60 * 1000).toISOString(),
        })
      }
    }
  })

  // 承認待ちの申込み（承認・却下を試すため）：手動のイベント 11 に、じろう（3、メンバー）とさくら（4、メンバー以外）
  const manualEvent = events[10]
  const pendingTime = new Date(manualEvent.createdAt).getTime() + 30 * 60 * 1000
  for (const [n, userId] of [3, 4].entries()) {
    applications.push({
      id: applications.length + 1,
      eventId: manualEvent.id,
      userId,
      status: 'pending',
      appliedAt: new Date(pendingTime + n * 60 * 1000).toISOString(),
      queuedAt: null,
    })
  }

  return applications
}

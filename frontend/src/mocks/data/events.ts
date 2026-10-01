/**
 * 【役割】偽の API が返す、偽のイベントのデータ。
 * 【なぜ必要か】バックエンドとデータベースができるまでの間、画面の動きを確かめるため。
 * 検索やページの切り替えを試せるように、70件ほどのイベントを自動で作る。
 * このファイルは開発中だけ使い、本番用のファイルには含まれない。
 */

/**
 * 【何のため】偽のデータベースに入っているイベント1件分の形。
 * 画面に返す形（EventSummary）より項目が多い。本物のデータベースのテーブルに近い形にしている。
 */
export type MockEvent = {
  id: number
  communityId: number
  communityName: string
  title: string
  // 説明（検索の対象になる）
  description: string
  // 開始日時と終了日時（世界標準時の ISO 8601 形式）
  startAt: string
  endAt: string
  capacity: number
  // 申込み済みの人数
  appliedCount: number
  // イベントが作られた日時（一覧の並び順に使う）
  createdAt: string
}

// 偽のコミュニティの名前。番号（communityId）は、この並びの順に 1, 2, 3... とする
const communityNames = [
  'Java 勉強会 東京',
  'フロントエンドもくもく会',
  'クラウド入門の会',
  'データベース読書会',
  'はじめてのプログラミング',
]

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

// 1時間と1日を、ミリ秒（1000分の1秒）で表したもの。日時の計算に使う
const HOUR = 60 * 60 * 1000
const DAY = 24 * HOUR

/**
 * 【何のため】偽のイベントを、決まったルールで70件作る。
 * 毎回同じデータになるように、乱数は使わず、番号から計算して作る。
 * 日時は「今」を基準にするので、いつ開いても「開催前」と「終了済み」のイベントが混ざる。
 */
function createMockEvents(): MockEvent[] {
  // 今の日時（ミリ秒）
  const now = Date.now()
  const events: MockEvent[] = []

  // i を 0 から 69 まで1つずつ増やしながら、70回くり返す
  for (let i = 0; i < 70; i++) {
    // % は「割った余り」。余りを使うと、一覧の中を順番にぐるぐる選べる
    const topic = topics[i % topics.length]
    const format = formats[i % formats.length]
    const communityIndex = i % communityNames.length

    // 開始日時：8件に1件は過去（終了済み）、それ以外は1〜60日後のどこか
    const isPast = i % 8 === 0
    const startAt = isPast ? now - (i + 1) * DAY : now + ((i % 60) + 1) * DAY
    // 終了日時：開始の2時間後
    const endAt = startAt + 2 * HOUR
    // 作成日時：番号が大きいほど新しい（i 時間前に作られたことにする）
    const createdAt = now - (70 - i) * HOUR

    // 定員：10〜40人。申込み済み：定員の範囲で、5件に1件は満席にする
    const capacity = 10 + (i % 4) * 10
    const appliedCount = i % 5 === 0 ? capacity : (i * 3) % capacity

    events.push({
      id: i + 1,
      communityId: communityIndex + 1,
      communityName: communityNames[communityIndex],
      title: `${topic} ${format}`,
      description: descriptions[i % descriptions.length],
      // new Date(ミリ秒).toISOString() で、世界標準時の ISO 8601 形式の文字列にする
      startAt: new Date(startAt).toISOString(),
      endAt: new Date(endAt).toISOString(),
      capacity,
      appliedCount,
      createdAt: new Date(createdAt).toISOString(),
    })
  }

  return events
}

// 作った偽のイベント。偽の API（src/mocks/handlers.ts）から読み込んで使う
export const mockEvents: MockEvent[] = createMockEvents()

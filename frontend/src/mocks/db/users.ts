/**
 * 【役割】偽のデータベースの「ユーザー」の表と、最初に入れておくデータ。
 * 【なぜ必要か】バックエンドができるまでの間、ログインやコミュニティの画面を試せるようにするため。
 * 開発中だけ使い、本番用のファイルには含まれない。
 */

/**
 * 【何のため】偽のデータベースのユーザー1人分の形。
 */
export type MockUser = {
  id: number
  realName: string
  nickname: string
  email: string
  // パスワード。偽物なので、そのまま保存している。
  // 本物のバックエンドでは、BCrypt で変換した値だけを保存する（非機能要件 3章）
  password: string
  createdAt: string
}

// 偽のユーザー全員に共通のパスワード（開発中に試すためのもの）
export const MOCK_PASSWORD = 'campfire2026'

/**
 * 【何のため】動作確認に使う、立場の違うユーザー。
 * README の「開発用のアカウント」に、同じ内容を書いている。
 */
const demoUsers: Omit<MockUser, 'createdAt'>[] = [
  // Omit<型, '項目'> は「その型から、指定した項目を除いた形」という意味
  { id: 1, realName: '山田 太郎', nickname: 'たろう', email: 'owner@example.com', password: MOCK_PASSWORD },
  { id: 2, realName: '佐藤 花子', nickname: 'はなこ', email: 'staff@example.com', password: MOCK_PASSWORD },
  { id: 3, realName: '鈴木 次郎', nickname: 'じろう', email: 'member@example.com', password: MOCK_PASSWORD },
  { id: 4, realName: '高橋 さくら', nickname: 'さくら', email: 'guest@example.com', password: MOCK_PASSWORD },
]

// 申込みなどを作るために使う、たくさんの参加者の人数
export const DUMMY_USER_COUNT = 40
// 参加者の番号は 5 から始まる（1〜4 は上の動作確認用のユーザー）
export const FIRST_DUMMY_USER_ID = 5

/**
 * 【何のため】ユーザーの表の最初のデータを作る。
 */
export function createUsers(now: number): MockUser[] {
  const users: MockUser[] = demoUsers.map((user) => ({
    // ...user は「user の項目をすべてここに並べる」という書き方
    ...user,
    createdAt: new Date(now - 100 * 24 * 60 * 60 * 1000).toISOString(),
  }))

  // 参加者01〜参加者40 を作る
  for (let i = 0; i < DUMMY_USER_COUNT; i++) {
    // padStart(2, '0') は、1桁の数の前に 0 を付けて2桁にする（例：1 → "01"）
    const number = String(i + 1).padStart(2, '0')
    users.push({
      id: FIRST_DUMMY_USER_ID + i,
      realName: `参加者 ${number}`,
      nickname: `参加者${number}`,
      email: `user${number}@example.com`,
      password: MOCK_PASSWORD,
      createdAt: new Date(now - (90 - i) * 24 * 60 * 60 * 1000).toISOString(),
    })
  }

  return users
}

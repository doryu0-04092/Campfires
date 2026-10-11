/**
 * 【役割】偽のデータベースの「コミュニティ」と「メンバー」の表と、最初に入れておくデータ。
 * 【なぜ必要か】コミュニティの画面や、立場（オーナー・運営スタッフ・メンバー）ごとの動きを試せるようにするため。
 */

import { DUMMY_USER_COUNT, FIRST_DUMMY_USER_ID } from './users'

/**
 * 【何のため】偽のデータベースのコミュニティ1件分の形。
 */
export type MockCommunity = {
  id: number
  name: string
  description: string
  // オーナーのユーザーの番号
  ownerId: number
  // アーカイブした日時。アーカイブしていなければ null（要件定義書 5.9）
  archivedAt: string | null
  createdAt: string
}

/**
 * 【何のため】コミュニティの中での立場。docs/api/openapi.yaml の CommunityRole と同じ。
 */
export type MockRole = 'owner' | 'staff' | 'member'

/**
 * 【何のため】偽のデータベースのメンバー1人分の形（どのユーザーが、どのコミュニティに、どの立場でいるか）。
 */
export type MockMembership = {
  communityId: number
  userId: number
  role: MockRole
  joinedAt: string
}

// 偽のコミュニティの名前、説明、オーナー。番号は、この並びの順に 1, 2, 3... とする
const communitySeeds = [
  { name: 'Java 勉強会 東京', description: 'Java を学ぶ人のための勉強会です。初心者も歓迎します。', ownerId: 1 },
  { name: 'フロントエンドもくもく会', description: 'React や TypeScript を、それぞれのペースで学ぶ会です。', ownerId: 1 },
  { name: 'クラウド入門の会', description: 'AWS や Docker の基本を、手を動かして学びます。', ownerId: FIRST_DUMMY_USER_ID },
  { name: 'データベース読書会', description: 'SQL とデータベース設計の本を、みんなで読み進めます。', ownerId: FIRST_DUMMY_USER_ID + 1 },
  { name: 'はじめてのプログラミング', description: 'プログラミングを始めたばかりの人が集まる会です。', ownerId: FIRST_DUMMY_USER_ID + 2 },
]

// 1日をミリ秒で表したもの
const DAY = 24 * 60 * 60 * 1000

/**
 * 【何のため】コミュニティの表の最初のデータを作る。
 */
export function createCommunities(now: number): MockCommunity[] {
  // map の2つ目の値（index）は、何番目か（0 から始まる）
  return communitySeeds.map((seed, index) => ({
    id: index + 1,
    ...seed,
    archivedAt: null,
    // 番号が大きいほど新しく作られたことにする
    createdAt: new Date(now - (60 - index * 5) * DAY).toISOString(),
  }))
}

/**
 * 【何のため】メンバーの表の最初のデータを作る。
 * - 各コミュニティのオーナー
 * - 動作確認用：はなこ（2）は Java 勉強会の運営スタッフ、じろう（3）は Java 勉強会のメンバー
 * - 参加者01〜40 は、すべてのコミュニティのメンバー（イベントに申し込めるようにするため）
 * さくら（4）は、どのコミュニティにも入っていない
 */
export function createMemberships(communities: MockCommunity[], now: number): MockMembership[] {
  const memberships: MockMembership[] = []
  const joinedAt = new Date(now - 50 * DAY).toISOString()

  for (const community of communities) {
    // オーナー
    memberships.push({ communityId: community.id, userId: community.ownerId, role: 'owner', joinedAt })

    // 参加者01〜40。そのコミュニティのオーナーは、すでに入っているので飛ばす
    for (let i = 0; i < DUMMY_USER_COUNT; i++) {
      const userId = FIRST_DUMMY_USER_ID + i
      if (userId !== community.ownerId) {
        memberships.push({ communityId: community.id, userId, role: 'member', joinedAt })
      }
    }
  }

  // Java 勉強会（1）の運営スタッフとメンバー
  memberships.push({ communityId: 1, userId: 2, role: 'staff', joinedAt })
  memberships.push({ communityId: 1, userId: 3, role: 'member', joinedAt })

  return memberships
}

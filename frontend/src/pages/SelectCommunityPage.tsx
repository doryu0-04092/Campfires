/**
 * 【役割】S-18「コミュニティを選ぶ」の画面。
 * URL は /manage/select/community-edit、/manage/select/members、/manage/select/event-create
 * 【なぜ必要か】左のメニューの「コミュニティ編集」「メンバー管理」「イベント作成」は、
 * どのコミュニティの画面かを決めないと開けない。メニューを押したあとに、この画面で対象のコミュニティを選ぶ（Issue #45）。
 * 選べるコミュニティは、何をするかによって変わる（オーナーだけが編集できる、など）。
 */

import { Link } from 'react-router'
import { fetchManagedCommunities } from '../api/communities'
import styles from '../components/List.module.css'
import LoadError from '../components/LoadError'
import { ROLE_LABELS, type ManagedCommunity } from '../types/community'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】この画面で何をするか。何をするかによって、見出し、選べるコミュニティ、選んだあとの移動先が変わる。
 */
export type SelectCommunityPurpose = 'community-edit' | 'members' | 'event-create'

/**
 * 【何のため】何をするかごとの設定。
 * - title：見出し
 * - canSelect：そのコミュニティを選べるかどうか
 * - to：選んだあとの移動先の URL
 * - emptyMessage：選べるコミュニティが1つもないときの文
 */
const SETTINGS: Record<
  SelectCommunityPurpose,
  { title: string; canSelect: (c: ManagedCommunity) => boolean; to: (id: number) => string; emptyMessage: string }
> = {
  'community-edit': {
    title: 'コミュニティ編集：コミュニティを選ぶ',
    // 編集できるのはオーナーだけ（要件定義書 3章）
    canSelect: (c) => c.myRole === 'owner',
    to: (id) => `/manage/communities/${id}/edit`,
    emptyMessage: 'オーナーをしているコミュニティは、まだありません。',
  },
  members: {
    title: 'メンバー管理：コミュニティを選ぶ',
    // オーナーと運営スタッフが使える
    canSelect: () => true,
    to: (id) => `/manage/communities/${id}/members`,
    emptyMessage: 'オーナーか運営スタッフをしているコミュニティは、まだありません。',
  },
  'event-create': {
    title: 'イベント作成：コミュニティを選ぶ',
    // アーカイブ中のコミュニティでは、イベントを作れない（要件定義書 5.9）
    canSelect: (c) => c.archivedAt === null,
    to: (id) => `/manage/communities/${id}/events/new`,
    emptyMessage: 'イベントを作れるコミュニティ（オーナーか運営スタッフをしていて、アーカイブしていないもの）は、まだありません。',
  },
}

/**
 * 【何のため】管理しているコミュニティを、全ページ分まとめて読み込む。
 * 管理しているコミュニティは数が少ないので、全部を読んでから、選べるものだけに絞る。
 */
async function fetchAllManagedCommunities(signal: AbortSignal): Promise<ManagedCommunity[]> {
  const all: ManagedCommunity[] = []
  let page = 1
  // do … while は「中を1回実行してから、条件を確かめて、くり返す」書き方
  let totalPages: number
  do {
    const response = await fetchManagedCommunities(page, signal)
    all.push(...response.items)
    totalPages = response.totalPages
    page++
  } while (page <= totalPages)
  return all
}

/**
 * 【何のため】コミュニティを選ぶ画面を表示する。
 */
function SelectCommunityPage({ purpose }: { purpose: SelectCommunityPurpose }) {
  const setting = SETTINGS[purpose]
  const { isLoading, data, error } = useLoader('managed-communities:all', fetchAllManagedCommunities)
  // 選べるコミュニティだけに絞る
  const communities = data?.filter(setting.canSelect) ?? []

  return (
    <section>
      <h1>{setting.title}</h1>
      {isLoading && <p className={styles.message}>読み込み中…</p>}
      {error !== undefined && <LoadError error={error} />}
      {data &&
        (communities.length === 0 ? (
          <p className={styles.message}>{setting.emptyMessage}</p>
        ) : (
          <ul className={styles.list}>
            {communities.map((community) => (
              <li key={community.id} className={styles.card}>
                <h2 className={styles.cardTitle}>
                  {/* 押すと、選んだコミュニティの画面へ移動する */}
                  <Link to={setting.to(community.id)}>{community.name}</Link>
                  <span className={styles.badge}>{ROLE_LABELS[community.myRole]}</span>
                  {community.archivedAt && <span className={`${styles.badge} ${styles.badgeMuted}`}>アーカイブ済み</span>}
                </h2>
                <p className={styles.cardMeta}>
                  メンバー {community.memberCount}人
                  {purpose === 'members' && community.pendingJoinRequestCount > 0 && (
                    <span className={`${styles.badge} ${styles.badgeAccent}`}>参加申請 {community.pendingJoinRequestCount}件</span>
                  )}
                </p>
              </li>
            ))}
          </ul>
        ))}
    </section>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default SelectCommunityPage

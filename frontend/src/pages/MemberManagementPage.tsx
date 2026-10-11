/**
 * 【役割】S-07「メンバー管理」の画面。URL は /manage/communities/:communityId/members
 * 【なぜ必要か】参加申請の承認・却下（F-07）、運営スタッフの任命・解除（F-09）、メンバーの除名（F-19）を行うため。
 * オーナーと運営スタッフが使える。任命・解除と除名は、オーナーだけ（要件定義書 3章）。
 * 表示する名前はニックネームだけ（要件定義書 5.6）。
 */

import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import {
  decideJoinRequest,
  fetchCommunity,
  fetchJoinRequests,
  fetchMembers,
  removeMember,
  updateMemberRole,
} from '../api/communities'
import formStyles from '../components/Form.module.css'
import styles from '../components/List.module.css'
import LoadError from '../components/LoadError'
import Pagination from '../components/Pagination'
import { ROLE_LABELS, type CommunityDetail, type Member } from '../types/community'
import { displayName } from '../types/user'
import { formatDateTime } from '../utils/formatDateTime'
import { pageHref, parsePage } from '../utils/page'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】メンバー管理の画面を表示する。
 * まずコミュニティの詳細を読み込み、自分の立場（オーナーか運営スタッフか）を確かめてから、中身を表示する。
 */
function MemberManagementPage() {
  const communityId = Number(useParams().communityId)
  const community = useLoader(`community:${communityId}`, (signal) => fetchCommunity(communityId, signal))

  if (community.isLoading) {
    return <p>読み込み中…</p>
  }
  if (community.error !== undefined || !community.data) {
    return <LoadError error={community.error} />
  }
  const myRole = community.data.myRole
  if (myRole !== 'owner' && myRole !== 'staff') {
    return <p className={formStyles.formError}>メンバー管理は、オーナーと運営スタッフだけが使えます。</p>
  }

  return <MemberManagementContent community={community.data} />
}

/**
 * 【何のため】参加申請の一覧と、メンバーの一覧を表示する。
 * 参加申請を承認するとメンバーが増えるので、そのときはメンバーの一覧も読み込み直す。
 */
function MemberManagementContent({ community }: { community: CommunityDetail }) {
  // メンバーの一覧を読み込み直すための番号。承認するたびに 1 増やす
  const [membersVersion, setMembersVersion] = useState(0)

  return (
    <section>
      <h1>メンバー管理：{community.name}</h1>
      <p>
        <Link to={`/communities/${community.id}`}>コミュニティの詳細へ戻る</Link>
      </p>
      <JoinRequestSection community={community} onApproved={() => setMembersVersion((v) => v + 1)} />
      <MemberSection community={community} version={membersVersion} />
    </section>
  )
}

/**
 * 【何のため】申請中の参加申請の一覧と、承認・却下のボタンを表示する（F-07）。
 */
function JoinRequestSection({ community, onApproved }: { community: CommunityDetail; onApproved: () => void }) {
  const { isLoading, data, error, reload } = useLoader(`join-requests:${community.id}`, (signal) =>
    fetchJoinRequests(community.id, signal),
  )
  const [actionError, setActionError] = useState<string | null>(null)
  // 処理中の申請の番号（その申請のボタンを押せないようにする）
  const [busyId, setBusyId] = useState<number | null>(null)

  async function decide(joinRequestId: number, status: 'approved' | 'rejected') {
    setBusyId(joinRequestId)
    setActionError(null)
    try {
      await decideJoinRequest(community.id, joinRequestId, status)
      reload()
      // 承認したときは、メンバーが増えるので、メンバーの一覧も読み込み直してもらう
      if (status === 'approved') {
        onApproved()
      }
    } catch (e) {
      // ほかの運営スタッフが先に処理した場合など。サーバーの文を出し、一覧を最新にする
      setActionError(e instanceof ApiError ? e.message : '通信中にエラーが発生しました。')
      reload()
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className={formStyles.section}>
      <h2>参加申請</h2>
      {community.archivedAt && <p className={styles.message}>アーカイブ中のため、新しい参加申請は届きません。</p>}
      {actionError && (
        <p className={formStyles.formError} role="alert">
          {actionError}
        </p>
      )}
      {isLoading && <p className={styles.message}>読み込み中…</p>}
      {error !== undefined && <LoadError error={error} />}
      {data && data.items.length === 0 && <p className={styles.message}>承認を待っている参加申請はありません。</p>}
      {data && data.items.length > 0 && (
        <>
          <p className={styles.message}>承認を待っている申請：{data.totalCount}件（申請した日時の古い順）</p>
          <ul className={styles.list}>
            {data.items.map((request) => (
              <li key={request.id} className={styles.card}>
                <strong>{displayName(request.user)}</strong>
                <span className={styles.cardMeta}>（申請日時：{formatDateTime(request.requestedAt)}）</span>
                <div className={styles.actions}>
                  <button
                    type="button"
                    className={`${styles.button} ${styles.buttonPrimary}`}
                    onClick={() => void decide(request.id, 'approved')}
                    disabled={busyId === request.id}
                  >
                    承認する
                  </button>
                  <button
                    type="button"
                    className={styles.button}
                    onClick={() => void decide(request.id, 'rejected')}
                    disabled={busyId === request.id}
                  >
                    却下する
                  </button>
                </div>
              </li>
            ))}
          </ul>
          {data.totalPages > 1 && <p className={styles.message}>古い順に30件を表示しています。処理すると、次の申請が表示されます。</p>}
        </>
      )}
    </div>
  )
}

/**
 * 【何のため】メンバーの一覧と、運営スタッフの任命・解除、除名のボタンを表示する（F-09、F-19）。
 * 任命・解除と除名のボタンは、オーナーにだけ出す。
 */
function MemberSection({ community, version }: { community: CommunityDetail; version: number }) {
  const [searchParams] = useSearchParams()
  const page = parsePage(searchParams.get('page'))
  // version が変わったとき（参加申請を承認したとき）も、読み込み直す
  const { isLoading, data, error, reload } = useLoader(`members:${community.id}:${page}:${version}`, (signal) =>
    fetchMembers(community.id, page, signal),
  )
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyUserId, setBusyUserId] = useState<number | null>(null)
  const isOwner = community.myRole === 'owner'

  /**
   * 【何のため】API を呼び、成功したら一覧を読み込み直す。
   */
  async function run(userId: number, action: () => Promise<unknown>) {
    setBusyUserId(userId)
    setActionError(null)
    try {
      await action()
      reload()
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : '通信中にエラーが発生しました。')
    } finally {
      setBusyUserId(null)
    }
  }

  /**
   * 【何のため】除名のボタンが押されたときの処理。
   * 申込みがあるかどうかに関係なく、申込みが自動でキャンセルされることを必ず伝えて、確認する（要件定義書 5.4.1）。
   */
  function handleRemove(member: Member) {
    const ok = window.confirm(
      `${displayName(member.user)} さんを除名しますか？\n除名すると、このメンバーの開催前のイベントへの申込み（キャンセル待ちを含む）は自動的にキャンセルされます。`,
    )
    if (ok) {
      void run(member.user.userId, () => removeMember(community.id, member.user.userId))
    }
  }

  return (
    <div className={formStyles.section}>
      <h2>メンバー</h2>
      {actionError && (
        <p className={formStyles.formError} role="alert">
          {actionError}
        </p>
      )}
      {isLoading && <p className={styles.message}>読み込み中…</p>}
      {error !== undefined && <LoadError error={error} />}
      {data && (
        <>
          <p className={styles.message}>{data.totalCount}人</p>
          <ul className={styles.list}>
            {data.items.map((member) => (
              <li key={member.user.userId} className={styles.card}>
                <strong>{displayName(member.user)}</strong>
                <span className={styles.badge}>{ROLE_LABELS[member.role]}</span>
                <span className={styles.cardMeta}>（参加：{formatDateTime(member.joinedAt)}）</span>
                {/* オーナーの行には操作を出さない。任命・解除と除名は、オーナーだけができる */}
                {isOwner && member.role !== 'owner' && (
                  <div className={styles.actions}>
                    <button
                      type="button"
                      className={styles.button}
                      onClick={() =>
                        void run(member.user.userId, () =>
                          updateMemberRole(community.id, member.user.userId, member.role === 'staff' ? 'member' : 'staff'),
                        )
                      }
                      disabled={busyUserId === member.user.userId}
                    >
                      {member.role === 'staff' ? '運営スタッフを解除する' : '運営スタッフに任命する'}
                    </button>
                    <button
                      type="button"
                      className={`${styles.button} ${styles.buttonDanger}`}
                      onClick={() => handleRemove(member)}
                      disabled={busyUserId === member.user.userId}
                    >
                      除名する
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            buildHref={(p) => pageHref(`/manage/communities/${community.id}/members`, p)}
          />
        </>
      )}
    </div>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default MemberManagementPage

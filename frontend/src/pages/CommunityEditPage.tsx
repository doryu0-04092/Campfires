/**
 * 【役割】S-06「コミュニティ編集」の画面。URL は /manage/communities/:communityId/edit
 * 【なぜ必要か】コミュニティの名前と説明を変え、アーカイブ・元に戻すため（F-05、要件定義書 5.9）。オーナーだけが使える。
 * アーカイブ中は、名前と説明を編集できない（元に戻してから編集する）。
 */

import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { archiveCommunity, fetchCommunity, unarchiveCommunity, updateCommunity } from '../api/communities'
import CommunityForm from '../components/CommunityForm'
import formStyles from '../components/Form.module.css'
import LoadError from '../components/LoadError'
import type { CommunityDetail } from '../types/community'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】コミュニティ編集の画面を表示する。
 */
function CommunityEditPage() {
  const communityId = Number(useParams().communityId)
  const { isLoading, data, error, reload } = useLoader(`community:${communityId}`, (signal) =>
    fetchCommunity(communityId, signal),
  )
  // 保存に成功したことを知らせる文
  const [message, setMessage] = useState<string | null>(null)

  if (isLoading) {
    return <p>読み込み中…</p>
  }
  if (error !== undefined || !data) {
    return <LoadError error={error} />
  }
  // オーナーでなければ、編集できないことを伝える（本当のチェックは API が行う）
  if (data.myRole !== 'owner') {
    return <p className={formStyles.formError}>コミュニティを編集できるのは、オーナーだけです。</p>
  }

  return (
    <section className={formStyles.container}>
      <h1>コミュニティを編集</h1>
      <p>
        <Link to={`/communities/${communityId}`}>「{data.name}」の詳細へ戻る</Link>
      </p>

      {message && (
        <p className={formStyles.success} role="status">
          {message}
        </p>
      )}
      {data.archivedAt && (
        <p className={formStyles.note}>アーカイブ中は、名前と説明を編集できません。下の「元に戻す」から戻してください。</p>
      )}

      {/* key に名前とアーカイブの状態を渡すと、それが変わったときに入力欄が新しい値で作り直される */}
      <CommunityForm
        key={`${data.name}:${data.archivedAt}`}
        initialValue={{ name: data.name, description: data.description }}
        submitLabel="保存する"
        disabled={data.archivedAt !== null}
        onSubmit={async (input) => {
          await updateCommunity(communityId, input)
          setMessage('保存しました。')
          reload()
        }}
      />

      <ArchiveSection community={data} onChanged={reload} />
    </section>
  )
}

/**
 * 【何のため】アーカイブする・元に戻すボタンを表示する（要件定義書 5.9）。
 */
function ArchiveSection({ community, onChanged }: { community: CommunityDetail; onChanged: () => void }) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

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
   * 【何のため】アーカイブのボタンが押されたときの処理。アーカイブ中にできなくなることを伝えて、確認する。
   */
  function handleArchive() {
    const ok = window.confirm(
      [
        `「${community.name}」をアーカイブしますか？`,
        '',
        '・コミュニティ一覧に表示されなくなります（詳細の画面は見られます）',
        '・新しいイベントの作成と、参加の申請ができなくなります',
        '・申請中の参加申請は、すべて却下されます',
        '・開催前のイベントは、そのまま開催されます',
        '・あとで元に戻せます',
      ].join('\n'),
    )
    if (ok) {
      void run(() => archiveCommunity(community.id))
    }
  }

  return (
    <div className={`${formStyles.section} ${formStyles.dangerSection}`}>
      <h2>アーカイブ</h2>
      {community.archivedAt ? (
        <>
          <p>このコミュニティはアーカイブされています。元に戻すと、編集や新しいイベントの作成ができるようになります。</p>
          <button type="button" className={formStyles.secondary} onClick={() => void run(() => unarchiveCommunity(community.id))} disabled={isSubmitting}>
            元に戻す
          </button>
        </>
      ) : (
        <>
          <p>活動を終えたコミュニティは、削除する代わりにアーカイブします。開催前のイベントは、そのまま開催されます。</p>
          <button type="button" className={formStyles.danger} onClick={handleArchive} disabled={isSubmitting}>
            アーカイブする
          </button>
        </>
      )}
      {error && (
        <p className={formStyles.formError} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default CommunityEditPage

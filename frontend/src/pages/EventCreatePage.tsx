/**
 * 【役割】S-08「イベント作成」の画面。URL は /manage/communities/:communityId/events/new
 * 【なぜ必要か】コミュニティにイベントを作るため（F-10）。オーナーと運営スタッフが使える。
 * アーカイブ中のコミュニティでは作れない（要件定義書 5.9）。
 * 作成したあとは、そのイベントの詳細（S-09）へ移動する（docs/screens.md 5章）。
 */

import { Link, useNavigate, useParams } from 'react-router'
import { fetchCommunity } from '../api/communities'
import { createEvent } from '../api/events'
import EventForm from '../components/EventForm'
import formStyles from '../components/Form.module.css'
import LoadError from '../components/LoadError'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】イベント作成の画面を表示する。
 */
function EventCreatePage() {
  const communityId = Number(useParams().communityId)
  const navigate = useNavigate()
  // 自分の立場とアーカイブの状態を確かめるため、コミュニティの詳細を読み込む
  const { isLoading, data, error } = useLoader(`community:${communityId}`, (signal) => fetchCommunity(communityId, signal))

  if (isLoading) {
    return <p>読み込み中…</p>
  }
  if (error !== undefined || !data) {
    return <LoadError error={error} />
  }
  if (data.myRole !== 'owner' && data.myRole !== 'staff') {
    return <p className={formStyles.formError}>イベントを作れるのは、オーナーと運営スタッフだけです。</p>
  }
  if (data.archivedAt) {
    return <p className={formStyles.formError}>アーカイブ中のコミュニティでは、イベントを作れません。</p>
  }

  return (
    <section className={formStyles.container}>
      <h1>イベントを作る</h1>
      <p>
        <Link to={`/communities/${communityId}`}>「{data.name}」の詳細へ戻る</Link>
      </p>
      <EventForm
        initialValue={null}
        submitLabel="作成する"
        onSubmit={async (input) => {
          const created = await createEvent(communityId, input)
          // 作成に成功したら、そのイベントの詳細へ移動する
          navigate(`/communities/${communityId}/events/${created.id}`)
        }}
      />
    </section>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default EventCreatePage

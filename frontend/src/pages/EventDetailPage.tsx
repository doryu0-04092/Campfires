/**
 * 【役割】S-09「イベント詳細」の画面。URL は /communities/:communityId/events/:eventId
 * 【なぜ必要か】docs/screens.md で決めた画面の1つ。
 * 今は仮の表示だけを置いている。中身は、画面を1つずつ作るときに書く。
 */

// useParams：URL の中の「:communityId」のような部分に入った値を取り出すための関数
import { useParams } from 'react-router'
// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】イベント詳細の画面を表示する。
 */
function EventDetailPage() {
  // URL から communityId, eventId を取り出す。
  // 例：/communities/3/events/5 なら、communityId は "3"、eventId は "5"
  const { communityId, eventId } = useParams()

  return (
    <PlaceholderPage
      screenId="S-09"
      title="イベント詳細"
      // `` で囲んだ文字の中の ${ } には、変数の値が入る
      description={`イベントの内容と残りの枠数を表示する。申込みとキャンセルもここで行う。（表示中：コミュニティ ${communityId}、イベント ${eventId}）`}
      // 動作確認のため、この画面から移動できる画面へのリンクを置く。
      // 管理の画面へのリンクは、本来はオーナーと運営スタッフにだけ表示する
      links={[
        { to: `/manage/communities/${communityId}/events/${eventId}/edit`, label: 'イベント編集（管理）' },
        { to: `/manage/communities/${communityId}/events/${eventId}/applicants`, label: '申込者一覧（管理）' },
      ]}
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default EventDetailPage

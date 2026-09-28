/**
 * 【役割】S-11「申込者一覧」の画面。URL は /manage/communities/:communityId/events/:eventId/applicants
 * 【なぜ必要か】docs/screens.md で決めた画面の1つ。
 * 今は仮の表示だけを置いている。中身は、画面を1つずつ作るときに書く。
 */

// useParams：URL の中の「:communityId」のような部分に入った値を取り出すための関数
import { useParams } from 'react-router'
// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】申込者一覧の画面を表示する。
 */
function ApplicantListPage() {
  // URL から communityId, eventId を取り出す。
  // 例：/manage/communities/3/events/5/applicants なら、communityId は "3"、eventId は "5"
  const { communityId, eventId } = useParams()

  return (
    <PlaceholderPage
      screenId="S-11"
      title="申込者一覧"
      // `` で囲んだ文字の中の ${ } には、変数の値が入る
      description={`申込者のニックネームと申込日時を表示する。（表示中：コミュニティ ${communityId}、イベント ${eventId}）`}
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default ApplicantListPage

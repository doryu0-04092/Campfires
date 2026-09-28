/**
 * 【役割】S-05「コミュニティ詳細」の画面。URL は /communities/:communityId
 * 【なぜ必要か】docs/screens.md で決めた画面の1つ。
 * 今は仮の表示だけを置いている。中身は、画面を1つずつ作るときに書く。
 */

// useParams：URL の中の「:communityId」のような部分に入った値を取り出すための関数
import { useParams } from 'react-router'
// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】コミュニティ詳細の画面を表示する。
 */
function CommunityDetailPage() {
  // URL から communityId を取り出す。
  // 例：/communities/3 なら、communityId は "3"
  const { communityId } = useParams()

  return (
    <PlaceholderPage
      screenId="S-05"
      title="コミュニティ詳細"
      // `` で囲んだ文字の中の ${ } には、変数の値が入る
      description={`コミュニティの情報とイベントの一覧を表示する。参加の申請や退会もここで行う。（表示中：コミュニティ ${communityId}）`}
      // 動作確認のため、この画面から移動できる画面へのリンクを置く
      links={[
        { to: `/communities/${communityId}/edit`, label: 'コミュニティ編集' },
        { to: `/communities/${communityId}/members`, label: 'メンバー管理' },
        { to: `/communities/${communityId}/events/new`, label: 'イベント作成' },
        { to: `/communities/${communityId}/events/1`, label: 'イベント詳細（例：1番）' },
      ]}
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default CommunityDetailPage

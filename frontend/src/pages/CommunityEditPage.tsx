/**
 * 【役割】S-06「コミュニティ編集」の画面。URL は /communities/:communityId/edit
 * 【なぜ必要か】docs/screens.md で決めた画面の1つ。
 * 今は仮の表示だけを置いている。中身は、画面を1つずつ作るときに書く。
 */

// useParams：URL の中の「:communityId」のような部分に入った値を取り出すための関数
import { useParams } from 'react-router'
// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】コミュニティ編集の画面を表示する。
 */
function CommunityEditPage() {
  // URL から communityId を取り出す。
  // 例：/communities/3/edit なら、communityId は "3"
  const { communityId } = useParams()

  return (
    <PlaceholderPage
      screenId="S-06"
      title="コミュニティ編集"
      // `` で囲んだ文字の中の ${ } には、変数の値が入る
      description={`コミュニティの名前と説明を変える。コミュニティの削除もここで行う。（表示中：コミュニティ ${communityId}）`}
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default CommunityEditPage

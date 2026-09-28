/**
 * 【役割】S-04「コミュニティ作成」の画面。URL は /manage/communities/new
 * 【なぜ必要か】docs/screens.md で決めた画面の1つ。
 * 今は仮の表示だけを置いている。中身は、画面を1つずつ作るときに書く。
 */

// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】コミュニティ作成の画面を表示する。
 */
function CommunityCreatePage() {
  return (
    <PlaceholderPage
      screenId="S-04"
      title="コミュニティ作成"
      description="名前と説明を入力して、コミュニティを作る。"
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default CommunityCreatePage

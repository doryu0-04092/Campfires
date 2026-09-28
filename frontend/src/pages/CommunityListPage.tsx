/**
 * 【役割】S-17「コミュニティ一覧」の画面。URL は /communities
 * 【なぜ必要か】すべてのコミュニティを探せるようにするため。
 * トップの画面（S-01）はイベントの一覧なので、まだイベントを開いていないコミュニティは、ここから見つける。
 * 今は仮の表示だけを置いている。中身は、画面を1つずつ作るときに書く。
 */

// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】コミュニティ一覧の画面を表示する。
 */
function CommunityListPage() {
  return (
    <PlaceholderPage
      screenId="S-17"
      title="コミュニティ一覧"
      description="すべてのコミュニティを、作成日時の新しい順に表示する。"
      // 動作確認のため、この画面から移動できる画面へのリンクを置く
      links={[{ to: '/communities/1', label: 'コミュニティ詳細（例：1番）' }]}
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default CommunityListPage

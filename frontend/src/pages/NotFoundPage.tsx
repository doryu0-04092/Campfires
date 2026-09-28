/**
 * 【役割】S-99「ページが見つかりません」の画面。URL は （どの URL にも当てはまらないとき）
 * 【なぜ必要か】docs/screens.md で決めた画面の1つ。
 * 今は仮の表示だけを置いている。中身は、画面を1つずつ作るときに書く。
 */

// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】ページが見つかりませんの画面を表示する。
 */
function NotFoundPage() {
  return (
    <PlaceholderPage
      screenId="S-99"
      title="ページが見つかりません"
      description="指定された URL の画面はありません。"
      // 動作確認のため、この画面から移動できる画面へのリンクを置く
      links={[
        { to: '/', label: 'コミュニティ一覧へ戻る' },
      ]}
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default NotFoundPage

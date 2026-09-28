/**
 * 【役割】S-01「イベント一覧（トップ）」の画面。URL は /
 * 【なぜ必要か】アプリを開いて最初に表示される画面。開催前のイベントを一覧で表示し、検索もここで行う。
 * 表示の並び順や検索のルールは、要件定義書の「5.8 イベント一覧と検索」で決めている。
 * 今は仮の表示だけを置いている。検索欄と一覧は、画面を1つずつ作るときに書く。
 */

// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】イベント一覧（トップ）の画面を表示する。
 */
function EventListPage() {
  return (
    <PlaceholderPage
      screenId="S-01"
      title="イベント一覧（トップ）"
      description="開催前のイベントを、作成日時の新しい順に表示する。単語を入力すると、タイトルと説明にその単語を含むイベントを探せる。"
      // 動作確認のため、この画面から移動できる画面へのリンクを置く
      links={[{ to: '/communities/1/events/1', label: 'イベント詳細（例：1番）' }]}
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default EventListPage

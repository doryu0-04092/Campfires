/**
 * 【役割】S-15「お知らせ一覧」の画面。URL は /notifications
 * 【なぜ必要か】docs/screens.md で決めた画面の1つ。
 * 今は仮の表示だけを置いている。中身は、画面を1つずつ作るときに書く。
 */

// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】お知らせ一覧の画面を表示する。
 */
function NotificationListPage() {
  return (
    <PlaceholderPage
      screenId="S-15"
      title="お知らせ一覧"
      description="締め切り後のキャンセルなどのお知らせを表示する。"
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default NotificationListPage

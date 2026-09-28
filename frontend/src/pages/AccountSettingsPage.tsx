/**
 * 【役割】S-12「アカウント設定」の画面。URL は /account
 * 【なぜ必要か】docs/screens.md で決めた画面の1つ。
 * 今は仮の表示だけを置いている。中身は、画面を1つずつ作るときに書く。
 */

// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】アカウント設定の画面を表示する。
 */
function AccountSettingsPage() {
  return (
    <PlaceholderPage
      screenId="S-12"
      title="アカウント設定"
      description="自分の情報を表示する。ニックネームの変更と、アカウントの削除もここで行う。"
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default AccountSettingsPage

/**
 * 【役割】S-03「ログイン」の画面。URL は /login
 * 【なぜ必要か】docs/screens.md で決めた画面の1つ。
 * 今は仮の表示だけを置いている。中身は、画面を1つずつ作るときに書く。
 */

// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】ログインの画面を表示する。
 */
function LoginPage() {
  return (
    <PlaceholderPage
      screenId="S-03"
      title="ログイン"
      description="メールアドレスとパスワードでログインする。"
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default LoginPage

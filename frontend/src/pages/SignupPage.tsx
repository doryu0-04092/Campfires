/**
 * 【役割】S-02「アカウント登録」の画面。URL は /signup
 * 【なぜ必要か】docs/screens.md で決めた画面の1つ。
 * 今は仮の表示だけを置いている。中身は、画面を1つずつ作るときに書く。
 */

// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】アカウント登録の画面を表示する。
 */
function SignupPage() {
  return (
    <PlaceholderPage
      screenId="S-02"
      title="アカウント登録"
      description="本名、ニックネーム、メールアドレス、パスワードを入力して登録する。プライバシーポリシーと利用規約への同意を求める。"
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default SignupPage

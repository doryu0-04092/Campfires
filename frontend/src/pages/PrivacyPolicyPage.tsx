/**
 * 【役割】S-13「プライバシーポリシー」の画面。URL は /privacy
 * 【なぜ必要か】docs/screens.md で決めた画面の1つ。
 * 今は仮の表示だけを置いている。中身は、画面を1つずつ作るときに書く。
 */

// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】プライバシーポリシーの画面を表示する。
 */
function PrivacyPolicyPage() {
  return (
    <PlaceholderPage
      screenId="S-13"
      title="プライバシーポリシー"
      description="個人情報の使い方を表示する。"
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default PrivacyPolicyPage

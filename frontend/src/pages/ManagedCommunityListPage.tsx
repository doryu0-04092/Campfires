/**
 * 【役割】S-16「管理しているコミュニティ一覧」の画面。URL は /manage/communities
 * 【なぜ必要か】管理の画面の入り口。管理の画面の多くは「どのコミュニティか」が決まらないと開けないため、
 * まずここで管理するコミュニティを選んでもらう。
 * 今は仮の表示だけを置いている。中身は、画面を1つずつ作るときに書く。
 */

// まだ中身を作っていない画面に、仮の表示を出すための部品
import PlaceholderPage from '../components/PlaceholderPage'

/**
 * 【何のため】管理しているコミュニティ一覧の画面を表示する。
 */
function ManagedCommunityListPage() {
  return (
    <PlaceholderPage
      screenId="S-16"
      title="管理しているコミュニティ一覧"
      description="自分がオーナーか運営スタッフをしているコミュニティの一覧を表示する。ここから各コミュニティの管理画面へ進む。"
      // 動作確認のため、この画面から移動できる画面へのリンクを置く（例として1番のコミュニティ）
      links={[
        { to: '/manage/communities/new', label: 'コミュニティ作成' },
        { to: '/manage/communities/1/edit', label: 'コミュニティ編集（例：1番）' },
        { to: '/manage/communities/1/members', label: 'メンバー管理（例：1番）' },
        { to: '/manage/communities/1/events/new', label: 'イベント作成（例：1番）' },
      ]}
    />
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default ManagedCommunityListPage

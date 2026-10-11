/**
 * 【役割】S-04「コミュニティ作成」の画面。URL は /manage/communities/new
 * 【なぜ必要か】名前と説明を入力して、コミュニティを作るため（F-03）。作った人がオーナーになる。
 * 作成したあとは、管理しているコミュニティの一覧（S-16）へ移動する（docs/screens.md 5章）。
 */

import { useNavigate } from 'react-router'
import { createCommunity } from '../api/communities'
import CommunityForm from '../components/CommunityForm'
import formStyles from '../components/Form.module.css'

/**
 * 【何のため】コミュニティ作成の画面を表示する。
 */
function CommunityCreatePage() {
  const navigate = useNavigate()

  return (
    <section className={formStyles.container}>
      <h1>コミュニティを作る</h1>
      <p className={formStyles.note}>作ったコミュニティでは、あなたがオーナーになります。</p>
      <CommunityForm
        initialValue={{ name: '', description: '' }}
        submitLabel="作成する"
        onSubmit={async (input) => {
          // 作成に成功したら、管理しているコミュニティの一覧へ移動する。
          // 失敗したときのエラーは、CommunityForm が受け取って表示する
          await createCommunity(input)
          navigate('/manage/communities')
        }}
      />
    </section>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default CommunityCreatePage

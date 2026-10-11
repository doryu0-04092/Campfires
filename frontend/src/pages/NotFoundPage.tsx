/**
 * 【役割】S-99「ページが見つかりません」の画面。どの URL にも当てはまらないときに表示する。
 * 【なぜ必要か】URL を打ち間違えたときなどに、何も表示されないのではなく、案内とトップへのリンクを出すため。
 */

import { Link } from 'react-router'
import styles from '../components/List.module.css'

/**
 * 【何のため】ページが見つからないことを伝え、トップへのリンクを出す。
 */
function NotFoundPage() {
  return (
    <section>
      <h1>ページが見つかりません</h1>
      <p className={styles.message}>URL が間違っているか、ページが移動・削除された可能性があります。</p>
      <Link to="/" className={`${styles.button} ${styles.buttonPrimary}`}>
        トップへ戻る
      </Link>
    </section>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default NotFoundPage

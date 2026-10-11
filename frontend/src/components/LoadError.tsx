/**
 * 【役割】データの読み込みに失敗したときの表示をする部品。
 * 【なぜ必要か】失敗の理由（見つからない、権限がない、通信の失敗）に合わせて、分かりやすい文を出すため。
 * どの画面でも同じ文にそろえる。
 */

import { ApiError } from '../api/client'
import styles from './Form.module.css'

/**
 * 【何のため】エラーの種類に合わせた文を表示する。
 */
function LoadError({ error }: { error: unknown }) {
  let message = '読み込めませんでした。時間をおいて、もう一度お試しください。'
  if (error instanceof ApiError) {
    if (error.status === 404) {
      message = '見つかりませんでした。削除されたか、URL が間違っている可能性があります。'
    } else if (error.status === 403) {
      message = 'この画面を見る権限がありません。'
    }
  }
  return (
    <p className={styles.formError} role="alert">
      {message}
    </p>
  )
}

export default LoadError

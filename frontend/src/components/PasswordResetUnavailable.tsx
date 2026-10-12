/**
 * 【役割】「パスワードの再設定は、今は使えません」という案内を表示する部品。
 * 【なぜ必要か】本番には、メールを送る仕組みがまだないので、パスワードの再設定を止めている（API は 503、
 * PASSWORD_RESET_UNAVAILABLE を返す）。その間は、運営者への問い合わせを案内する。
 * 「パスワードを忘れた場合」（S-20）と「パスワードの再設定」（S-21）の2つの画面で、同じ文を出すため、部品にまとめる。
 */

import { Link } from 'react-router'
import styles from './Form.module.css'

/**
 * 【何のため】再設定を使えないことと、問い合わせ先の案内を表示する。
 * role="alert"：表示されたときに、読み上げソフトにすぐ読み上げてもらう。
 */
function PasswordResetUnavailable() {
  return (
    <p className={styles.formError} role="alert">
      現在、パスワードの再設定はご利用いただけません。お手数ですが、運営者にお問い合わせください（問い合わせ先は
      <Link to="/privacy">プライバシーポリシー</Link>
      の「お問い合わせ先」に記載しています）。
    </p>
  )
}

export default PasswordResetUnavailable

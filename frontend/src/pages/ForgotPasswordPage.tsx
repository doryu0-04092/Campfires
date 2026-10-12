/**
 * 【役割】S-20「パスワードを忘れた場合」の画面。URL は /password/forgot
 * 【なぜ必要か】パスワードを忘れた人が、メールアドレスを入力して、パスワードの再設定のメールを依頼するため（F-25）。
 *
 * 登録されているメールアドレスかどうかは、画面にも出さない（API もいつも同じ返事。メールアドレスを探られないように）。
 * 本番では、メールを送る仕組みがまだないので、再設定を止めている。そのときは、問い合わせを案内する。
 */

import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { fetchAuthFeatures, requestPasswordReset } from '../api/auth'
import { ApiError } from '../api/client'
import FormField from '../components/FormField'
import PasswordResetUnavailable from '../components/PasswordResetUnavailable'
import formStyles from '../components/Form.module.css'
import { toFieldMessages } from '../utils/fieldErrors'
import { useLoader } from '../utils/useLoader'

/**
 * 【何のため】パスワードを忘れた場合の画面を表示する。
 */
function ForgotPasswordPage() {
  // 今使える機能の一覧。再設定を止めているなら、最初から案内を出して、入力欄は出さない
  const { data: features } = useLoader('auth-features', (signal) => fetchAuthFeatures(signal))

  const [email, setEmail] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [emailError, setEmailError] = useState<string | undefined>(undefined)
  const [formError, setFormError] = useState<string | null>(null)
  // 依頼を受け付けたことを知らせる文
  const [message, setMessage] = useState<string | null>(null)
  // 依頼したら API が 503（再設定を止めている）を返したかどうか
  const [isUnavailable, setIsUnavailable] = useState(false)

  /**
   * 【何のため】「再設定のメールを送る」のボタンが押されたときの処理。
   */
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)
    setEmailError(undefined)
    setFormError(null)
    setMessage(null)

    try {
      await requestPasswordReset({ email })
      // 登録されていても、いなくても、同じ文を出す
      setMessage(
        '登録されているメールアドレスなら、再設定のメールを送りました。メールのリンクから、30分以内に新しいパスワードを決めてください。メールが届かない場合は、入力したメールアドレスを確かめて、もう一度お試しください。',
      )
    } catch (e) {
      if (!(e instanceof ApiError)) {
        setFormError('通信中にエラーが発生しました。時間をおいて、もう一度お試しください。')
      } else if (e.code === 'PASSWORD_RESET_UNAVAILABLE') {
        setIsUnavailable(true)
      } else if (e.code === 'VALIDATION_ERROR') {
        setEmailError(toFieldMessages(e.fieldErrors).email ?? e.message)
      } else if (e.code === 'RATE_LIMITED') {
        // 短い時間に何度も依頼した：サーバーの文（しばらく待ってから）をそのまま出す
        setFormError(e.message)
      } else {
        setFormError('依頼できませんでした。時間をおいて、もう一度お試しください。')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  // 再設定を止めている（一覧で分かった、または依頼で 503 が返った）ときは、案内だけを出す
  const unavailable = isUnavailable || features?.passwordResetEnabled === false

  return (
    <section className={formStyles.container}>
      <h1>パスワードを忘れた場合</h1>

      {unavailable ? (
        <PasswordResetUnavailable />
      ) : (
        <>
          <p>
            登録したメールアドレスを入力してください。パスワードの再設定のリンクを、メールでお送りします。
          </p>
          {/* role="status"：読み上げソフトに、受け付けた知らせを（作業を邪魔しない形で）読み上げてもらう */}
          {message && (
            <p className={formStyles.success} role="status">
              {message}
            </p>
          )}
          {formError && (
            <p className={formStyles.formError} role="alert">
              {formError}
            </p>
          )}
          {/* noValidate：ブラウザの入力チェックを止め、エラーの表示を API の返事にそろえる */}
          <form className={formStyles.form} onSubmit={handleSubmit} noValidate>
            <FormField
              id="forgot-email"
              label="メールアドレス"
              type="email"
              autoComplete="email"
              maxLength={254}
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              error={emailError}
            />
            <button type="submit" className={formStyles.submit} disabled={isSubmitting}>
              {isSubmitting ? '送信中…' : '再設定のメールを送る'}
            </button>
          </form>
        </>
      )}

      <p className={formStyles.note}>
        <Link to="/login">ログインの画面に戻る</Link>
      </p>
    </section>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default ForgotPasswordPage

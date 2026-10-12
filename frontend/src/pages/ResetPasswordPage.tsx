/**
 * 【役割】S-21「パスワードの再設定」の画面。URL は /password/reset#token=…（メールのリンクから開く）
 * 【なぜ必要か】再設定のメールのリンクから来た人が、新しいパスワードを決めるため（F-25）。
 *
 * リンクの token（使い捨ての合言葉）は、URL の「#」の後ろ（フラグメント）に入っている。「#」より後ろは、ブラウザが
 * サーバーへ送らないので、画面を開いても、サーバーのログやトレースに残らない（Referer にも入らない）。
 * それでも、ブラウザの履歴には残るので、画面を開いたときに1回だけ読み取り、すぐに URL から消す。
 * token は画面のメモリ（state）にだけ持ち、localStorage などには保存しない。
 * 成功したら、自動ではログインせず、ログインの画面へ移動して、新しいパスワードでログインしてもらう。
 */

import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { resetPassword } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import PasswordField from '../components/PasswordField'
import PasswordResetUnavailable from '../components/PasswordResetUnavailable'
import formStyles from '../components/Form.module.css'
import { toFieldMessages } from '../utils/fieldErrors'

// ログインの画面に渡す、成功の知らせ
const SUCCESS_NOTICE = 'パスワードを再設定しました。新しいパスワードでログインしてください。'

/**
 * 【何のため】パスワードの再設定の画面を表示する。
 */
function ResetPasswordPage() {
  const { state, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  // URL の「#token=…」から token を、最初の1回だけ読み取って覚えておく（useState に関数を渡すと、最初の表示のときだけ実行される）。
  // location.hash は「#token=…」の部分。slice(1) で先頭の「#」を取り、URLSearchParams で「名前=値」を読む
  const [token] = useState(() => new URLSearchParams(location.hash.slice(1)).get('token') ?? '')

  // 画面を開いたら、URL から token を消す。replace: true で、履歴にも token 付きの URL を残さない
  // （中では、ブラウザの history.replaceState が使われる）
  useEffect(() => {
    if (location.hash !== '') {
      navigate({ pathname: location.pathname, search: location.search }, { replace: true })
    }
  }, [location.hash, location.pathname, location.search, navigate])

  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [fieldMessages, setFieldMessages] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  // リンクが使えない（知らない・期限切れ・使用済み）とき true。入力欄を隠し、もう一度依頼する案内を出す
  const [isTokenInvalid, setIsTokenInvalid] = useState(token === '')
  // 再設定を止めている（API が 503 を返した）とき true
  const [isUnavailable, setIsUnavailable] = useState(false)

  /**
   * 【何のため】「パスワードを再設定する」のボタンが押されたときの処理。
   */
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFieldMessages({})
    setFormError(null)

    // 新しいパスワードと、確認のための入力が同じかを、送る前に画面で確かめる
    if (newPassword !== confirmPassword) {
      setFieldMessages({ confirmPassword: '確認のため、新しいパスワードと同じものを入力してください。' })
      return
    }

    setIsSubmitting(true)
    try {
      await resetPassword({ token, newPassword })
      const goToLogin = () => navigate('/login', { replace: true, state: { notice: SUCCESS_NOTICE } })
      if (state.status === 'authenticated') {
        // この端末でログインしていた場合：サーバーでは、そのログインはもう使えない（パスワードを変えたため）。
        // 画面の状態もログアウトにそろえてから、ログインの画面へ移動する
        await logout(goToLogin)
      } else {
        await goToLogin()
      }
    } catch (e) {
      if (!(e instanceof ApiError)) {
        setFormError('通信中にエラーが発生しました。時間をおいて、もう一度お試しください。')
      } else if (e.code === 'INVALID_RESET_TOKEN') {
        setIsTokenInvalid(true)
      } else if (e.code === 'PASSWORD_RESET_UNAVAILABLE') {
        setIsUnavailable(true)
      } else if (e.code === 'VALIDATION_ERROR') {
        const messages = toFieldMessages(e.fieldErrors)
        if (messages.token) {
          // token の形が正しくない（リンクが途中で切れているなど）：リンクが使えないときと同じ案内にする
          setIsTokenInvalid(true)
        } else {
          setFieldMessages(messages)
        }
      } else {
        setFormError('再設定できませんでした。時間をおいて、もう一度お試しください。')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className={formStyles.container}>
      <h1>パスワードの再設定</h1>

      {isUnavailable ? (
        <PasswordResetUnavailable />
      ) : isTokenInvalid ? (
        // role="alert"：表示されたときに、読み上げソフトにすぐ読み上げてもらう
        <p className={formStyles.formError} role="alert">
          このリンクは使えません。有効期限（30分）が切れたか、すでに使われています。お手数ですが、
          <Link to="/password/forgot">もう一度、再設定を依頼してください</Link>。
        </p>
      ) : (
        <>
          <p>新しいパスワードを入力してください。</p>
          {formError && (
            <p className={formStyles.formError} role="alert">
              {formError}
            </p>
          )}
          <form className={formStyles.form} onSubmit={handleSubmit} noValidate>
            <PasswordField
              id="reset-new-password"
              label="新しいパスワード"
              // new-password：ブラウザに「新しいパスワードを作る欄」だと伝え、強いパスワードの候補を出してもらう
              autoComplete="new-password"
              maxLength={64}
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              hint="8〜64文字。半角の英字・数字・記号が使えます。"
              error={fieldMessages.newPassword}
            />
            <PasswordField
              id="reset-new-password-confirm"
              label="新しいパスワード（確認）"
              autoComplete="new-password"
              maxLength={64}
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              error={fieldMessages.confirmPassword}
            />
            <p className={formStyles.note}>
              再設定すると、ほかの端末でのログインは、すべてログアウトになります。
            </p>
            <button
              type="submit"
              className={formStyles.submit}
              disabled={isSubmitting || newPassword === '' || confirmPassword === ''}
            >
              {isSubmitting ? '再設定中…' : 'パスワードを再設定する'}
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
export default ResetPasswordPage

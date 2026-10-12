/**
 * 【役割】S-02「アカウント登録」の画面。URL は /signup
 * 【なぜ必要か】本名、ニックネーム、メールアドレス、パスワードを登録するため（F-01）。
 * プライバシーポリシーと利用規約に同意しないと、登録のボタンを押せない（F-18、要件定義書 5.7）。
 * 登録に成功すると、そのままログインした状態になる。
 */

import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import FormField from '../components/FormField'
import PasswordField from '../components/PasswordField'
import formStyles from '../components/Form.module.css'
import { toFieldMessages } from '../utils/fieldErrors'
import { returnToPath, returnToState } from '../utils/returnTo'

/**
 * 【何のため】アカウント登録の画面を表示する。
 */
function SignupPage() {
  const { state, signup } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  // 登録のあとに戻る画面（「申し込む」や上の「アカウント登録」を押したときの画面。なければトップ）
  const from = returnToPath(location)

  // 入力中の値
  const [realName, setRealName] = useState('')
  const [nickname, setNickname] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  // プライバシーポリシーと利用規約に同意したか（チェックボックス）
  const [agreed, setAgreed] = useState(false)

  const [isSubmitting, setIsSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldMessages, setFieldMessages] = useState<Record<string, string>>({})

  // すでにログインしているときは、登録の画面は不要なので、元の画面に移動する
  if (state.status === 'authenticated') {
    return <Navigate to={from} replace />
  }

  /**
   * 【何のため】登録のボタンが押されたときの処理。
   */
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)
    setFormError(null)
    setFieldMessages({})

    try {
      await signup({ realName, nickname, email, password, agreedToPolicies: agreed })
      // 成功したら、ログインした状態でトップの画面へ移動する
      navigate(from, { replace: true })
    } catch (error) {
      if (!(error instanceof ApiError)) {
        setFormError('通信中にエラーが発生しました。時間をおいて、もう一度お試しください。')
      } else if (error.code === 'VALIDATION_ERROR') {
        setFieldMessages(toFieldMessages(error.fieldErrors))
      } else if (error.code === 'EMAIL_ALREADY_REGISTERED') {
        // すでに登録されているメールアドレス：メールアドレスの入力欄の下に出す
        setFieldMessages({ email: error.message })
      } else {
        setFormError('登録できませんでした。時間をおいて、もう一度お試しください。')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className={formStyles.container}>
      <h1>アカウント登録</h1>

      {formError && (
        <p className={formStyles.formError} role="alert">
          {formError}
        </p>
      )}

      <form className={formStyles.form} onSubmit={handleSubmit} noValidate>
        <FormField
          id="signup-real-name"
          label="本名"
          autoComplete="name"
          maxLength={50}
          value={realName}
          onChange={(event) => setRealName(event.target.value)}
          // 本名がほかの人に見えないことを、入力の前に伝える（要件定義書 5.6）
          hint="本名は、ほかのユーザー（コミュニティのオーナーや運営スタッフを含む）には表示されません。"
          error={fieldMessages.realName}
        />
        <FormField
          id="signup-nickname"
          label="ニックネーム"
          autoComplete="nickname"
          maxLength={30}
          value={nickname}
          onChange={(event) => setNickname(event.target.value)}
          hint="ほかのユーザーに表示される名前です。あとから変更できます。"
          error={fieldMessages.nickname}
        />
        <FormField
          id="signup-email"
          label="メールアドレス"
          type="email"
          autoComplete="email"
          maxLength={254}
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          hint="ほかのユーザーには表示されません。"
          error={fieldMessages.email}
        />
        {/* パスワードの入力欄。「パスワードを表示する」にチェックを入れると、入力した文字が見える */}
        <PasswordField
          id="signup-password"
          label="パスワード"
          // new-password：ブラウザに「新しいパスワードを作る欄」だと伝え、強いパスワードの候補を出してもらう
          autoComplete="new-password"
          maxLength={64}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          hint="8〜64文字。半角の英字・数字・記号が使えます。"
          error={fieldMessages.password}
        />

        {/* 同意のチェックボックス。リンクは新しいタブで開き、入力中の内容が消えないようにする */}
        <div>
          <label className={formStyles.checkbox}>
            <input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} />
            <span>
              {/* target="_blank" は新しいタブで開く指定。rel="noopener noreferrer" は、開いたページから元のページを操作されないようにする安全のための指定 */}
              <a href="/privacy" target="_blank" rel="noopener noreferrer">プライバシーポリシー</a>
              と
              <a href="/terms" target="_blank" rel="noopener noreferrer">利用規約</a>
              に同意します
            </span>
          </label>
          {fieldMessages.agreedToPolicies && <p className={formStyles.fieldError}>{fieldMessages.agreedToPolicies}</p>}
        </div>

        {/* 同意していないときと、送信中は押せない */}
        <button type="submit" className={formStyles.submit} disabled={!agreed || isSubmitting}>
          {isSubmitting ? '登録中…' : '登録する'}
        </button>
      </form>

      <p className={formStyles.note}>
        すでにアカウントをお持ちの方は <Link to="/login" state={returnToState(location)}>ログイン</Link>
      </p>
    </section>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default SignupPage

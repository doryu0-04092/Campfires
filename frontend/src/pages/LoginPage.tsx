/**
 * 【役割】S-03「ログイン」の画面。URL は /login
 * 【なぜ必要か】メールアドレスとパスワードでログインするため（F-02）。
 * 失敗したときの表示は、API が返すエラーの code（docs/api/openapi.yaml の login）で切り替える。
 * ログインが必要な画面から移動してきた場合は、ログインのあと、その画面に戻る。
 * パスワードの再設定（F-25）を使えるときだけ、「パスワードを忘れた場合」のリンクを出す（使える機能の一覧 GET /api/auth/features で判断）。
 */

import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { fetchAuthFeatures } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import FormField from '../components/FormField'
import PasswordField from '../components/PasswordField'
import formStyles from '../components/Form.module.css'
import { toFieldMessages } from '../utils/fieldErrors'
import { useLoader } from '../utils/useLoader'
import { returnToPath, returnToState } from '../utils/returnTo'
import styles from './LoginPage.module.css'

/**
 * 【何のため】ログインの画面を表示する。
 */
function LoginPage() {
  const { state, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  // 元の画面（ログインが必要な画面、「申し込む」を押した画面、上の「ログイン」を押したときの画面）が state.from に入っている。
  // ログインのあとは、その画面に戻る（検索の言葉も残す）。来た場所がなければトップ（/）に戻る（utils/returnTo.ts）
  const from = returnToPath(location)
  // ほかの画面から渡された知らせ（例：パスワードの再設定の画面から「再設定しました」）
  const notice = (location.state as { notice?: string } | null)?.notice
  // 今使える機能の一覧。読み込めなかったときは、リンクを出さない（「お問い合わせください」の案内を出す）
  const { data: features } = useLoader('auth-features', (signal) => fetchAuthFeatures(signal))

  // 入力中のメールアドレスとパスワード
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  // 送信中かどうか。送信中はボタンを押せないようにして、二重に送られるのを防ぐ
  const [isSubmitting, setIsSubmitting] = useState(false)
  // 画面の上に出すエラーの文（なければ null）
  const [formError, setFormError] = useState<string | null>(null)
  // 入力欄ごとのエラーの文
  const [fieldMessages, setFieldMessages] = useState<Record<string, string>>({})

  // すでにログインしているときは、ログインの画面は不要なので、元の画面に移動する。
  // Navigate は、表示されたときに別の画面へ移動する部品。replace は「戻る」でここに戻ってこないようにする指定
  if (state.status === 'authenticated') {
    return <Navigate to={from} replace />
  }

  /**
   * 【何のため】ログインのボタンが押されたときの処理。
   */
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    // ブラウザがページ全体を読み込み直すのを止める
    event.preventDefault()
    setIsSubmitting(true)
    setFormError(null)
    setFieldMessages({})

    try {
      await login({ email, password })
      // 成功したら、元の画面に移動する
      navigate(from, { replace: true })
    } catch (error) {
      // API のエラーなら、code を見て表示を変える。それ以外（通信の失敗など）は、まとめて1つの文にする
      if (!(error instanceof ApiError)) {
        setFormError('通信中にエラーが発生しました。時間をおいて、もう一度お試しください。')
      } else if (error.code === 'VALIDATION_ERROR') {
        // 入力のチェックで引っかかった：入力欄ごとに文を出す
        setFieldMessages(toFieldMessages(error.fieldErrors))
      } else if (error.code === 'INVALID_CREDENTIALS' || error.code === 'ACCOUNT_LOCKED') {
        // メールアドレスかパスワードが違う、または、ロックされている：サーバーの文をそのまま出す
        setFormError(error.message)
      } else {
        setFormError('ログインできませんでした。時間をおいて、もう一度お試しください。')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className={formStyles.container}>
      <h1>ログイン</h1>

      {/* 成功の知らせ。role="status" で、読み上げソフトに（作業を邪魔しない形で）読み上げてもらう */}
      {notice && (
        <p className={formStyles.success} role="status">
          {notice}
        </p>
      )}

      {/* エラーの文。role="alert" で、読み上げソフトにすぐに読み上げてもらう */}
      {formError && (
        <p className={formStyles.formError} role="alert">
          {formError}
        </p>
      )}

      {/* noValidate：ブラウザの入力チェックを止め、エラーの表示を API の返事にそろえる */}
      <form className={formStyles.form} onSubmit={handleSubmit} noValidate>
        <FormField
          id="login-email"
          label="メールアドレス"
          type="email"
          // autoComplete：ブラウザに保存したメールアドレスを、候補として出してもらう
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={fieldMessages.email}
        />
        {/* パスワードの入力欄。「パスワードを表示する」にチェックを入れると、入力した文字が見える */}
        <PasswordField
          id="login-password"
          label="パスワード"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={fieldMessages.password}
        />
        <button type="submit" className={formStyles.submit} disabled={isSubmitting}>
          {isSubmitting ? 'ログイン中…' : 'ログイン'}
        </button>
      </form>

      <p className={formStyles.note}>
        アカウントをお持ちでない方は <Link to="/signup" state={returnToState(location)}>アカウント登録</Link>
      </p>
      {/* パスワードの再設定を使えるときはリンクを、使えないとき（メールを送る仕組みがない本番など）は案内を出す */}
      {features?.passwordResetEnabled ? (
        <p className={formStyles.note}>
          <Link to="/password/forgot">パスワードを忘れた場合</Link>
        </p>
      ) : (
        <p className={formStyles.note}>パスワードを忘れた場合は、お問い合わせください。</p>
      )}

      {/* 開発中だけ、偽のアカウントの一覧を表示する（本番用のファイルには含まれない） */}
      {import.meta.env.DEV && <DevAccounts />}
    </section>
  )
}

/**
 * 【何のため】開発中に試せる、偽のアカウントの一覧を表示する。
 * パスワードは src/mocks/db/users.ts の MOCK_PASSWORD と同じ。
 */
function DevAccounts() {
  return (
    <aside className={styles.devAccounts}>
      <h2>開発用のアカウント（パスワードはすべて campfire2026）</h2>
      <ul>
        <li>owner@example.com：たろう（Java 勉強会、フロントエンドもくもく会のオーナー）</li>
        <li>staff@example.com：はなこ（Java 勉強会の運営スタッフ）</li>
        <li>member@example.com：じろう（Java 勉強会のメンバー）</li>
        <li>guest@example.com：さくら（どのコミュニティにも入っていない）</li>
      </ul>
    </aside>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default LoginPage

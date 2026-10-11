/**
 * 【役割】S-12「アカウント設定」の画面。URL は /account
 * 【なぜ必要か】自分の情報の確認、ニックネームの変更（F-21）、アカウントの削除（F-17）を行うため。
 * ログインが必要な画面なので、src/router.tsx で RequireAuth の下に置いている。
 */

import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import FormField from '../components/FormField'
import formStyles from '../components/Form.module.css'
import type { Me } from '../types/account'
import { formatDateTime } from '../utils/formatDateTime'
import { toFieldMessages } from '../utils/fieldErrors'
import styles from './AccountSettingsPage.module.css'

/**
 * 【何のため】アカウント設定の画面を表示する。
 * RequireAuth の下にあるので、ここに来たときは必ずログインしている。
 */
function AccountSettingsPage() {
  const { state } = useAuth()

  // 念のため、ログインしていない場合は何も表示しない（RequireAuth があるので、通常は起きない）
  if (state.status !== 'authenticated') {
    return null
  }

  return (
    <section className={formStyles.container}>
      <h1>アカウント設定</h1>
      <Profile me={state.me} />
      {/* key は付けない（付けると、変更に成功したときにフォームが作り直され、「変更しました」の知らせが消えてしまうため） */}
      <NicknameForm currentNickname={state.me.nickname} />
      <DeleteAccount />
    </section>
  )
}

/**
 * 【何のため】自分の情報（本名、ニックネーム、メールアドレス、登録日）を表示する。
 * 本名とメールアドレスは、本人のこの画面でだけ見られる（要件定義書 5.6）。
 */
function Profile({ me }: { me: Me }) {
  return (
    <div className={formStyles.section}>
      <h2>登録している情報</h2>
      <dl className={styles.profile}>
        <dt>本名</dt>
        <dd>{me.realName}</dd>
        <dt>ニックネーム</dt>
        <dd>{me.nickname}</dd>
        <dt>メールアドレス</dt>
        <dd>{me.email}</dd>
        <dt>登録日</dt>
        <dd>{formatDateTime(me.createdAt)}</dd>
      </dl>
      <p className={formStyles.note}>
        本名とメールアドレスは、ほかのユーザーには表示されません。変更したい場合は、お問い合わせください。
      </p>
    </div>
  )
}

/**
 * 【何のため】ニックネームを変更するフォームを表示する（F-21）。
 */
function NicknameForm({ currentNickname }: { currentNickname: string }) {
  const { updateMe } = useAuth()
  const [nickname, setNickname] = useState(currentNickname)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | undefined>(undefined)
  // 変更に成功したことを知らせる文
  const [message, setMessage] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)
    setError(undefined)
    setMessage(null)

    try {
      await updateMe({ nickname })
      // 成功すると、ログイン状態の自分の情報が新しくなり、画面上部の表示も変わる
      setMessage('ニックネームを変更しました。')
    } catch (e) {
      if (e instanceof ApiError && e.code === 'VALIDATION_ERROR') {
        setError(toFieldMessages(e.fieldErrors).nickname)
      } else {
        setError('変更できませんでした。時間をおいて、もう一度お試しください。')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className={formStyles.section}>
      <h2>ニックネームの変更</h2>
      {/* role="status"：読み上げソフトに、成功の知らせを（作業を邪魔しない形で）読み上げてもらう */}
      {message && (
        <p className={formStyles.success} role="status">
          {message}
        </p>
      )}
      <form className={formStyles.form} onSubmit={handleSubmit} noValidate>
        <FormField
          id="account-nickname"
          label="新しいニックネーム"
          maxLength={30}
          value={nickname}
          onChange={(event) => setNickname(event.target.value)}
          hint="変更すると、過去の申込みの記録も含めて、すべての画面で新しいニックネームが表示されます。"
          error={error}
        />
        <div className={formStyles.actions}>
          {/* 今と同じニックネームのときは、変更する意味がないので押せない */}
          <button
            type="submit"
            className={formStyles.submit}
            disabled={isSubmitting || nickname.trim() === currentNickname}
          >
            {isSubmitting ? '変更中…' : '変更する'}
          </button>
        </div>
      </form>
    </div>
  )
}

/**
 * 【何のため】アカウントを削除する操作を表示する（F-17）。
 * 間違って押さないように、2段階にしている。
 * ① 「アカウントを削除する」を押すと、確認の欄が開く
 * ② パスワードを入力して「削除を確定する」を押すと、削除される（要件定義書 5.5）
 */
function DeleteAccount() {
  const { deleteAccount } = useAuth()
  const navigate = useNavigate()
  // 確認の欄が開いているかどうか
  const [isConfirming, setIsConfirming] = useState(false)
  const [password, setPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [passwordError, setPasswordError] = useState<string | undefined>(undefined)
  const [formError, setFormError] = useState<string | null>(null)
  // コミュニティの管理の画面への案内を出すかどうか（オーナーをしているコミュニティがあるとき）
  const [showManageLink, setShowManageLink] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)
    setPasswordError(undefined)
    setFormError(null)
    setShowManageLink(false)

    try {
      // 削除に成功したら、トップの画面へ移動してから、ログインしていない状態にする（AuthProvider の becomeGuest）
      await deleteAccount({ password }, () => navigate('/', { replace: true }))
    } catch (e) {
      if (!(e instanceof ApiError)) {
        setFormError('通信中にエラーが発生しました。時間をおいて、もう一度お試しください。')
      } else if (e.code === 'VALIDATION_ERROR' || e.code === 'INVALID_PASSWORD') {
        // パスワードが空、または違う：パスワードの入力欄の下に出す
        setPasswordError(toFieldMessages(e.fieldErrors).password ?? e.message)
      } else if (e.code === 'OWNS_ACTIVE_COMMUNITIES' || e.code === 'OWNED_COMMUNITY_HAS_UPCOMING_EVENTS') {
        // オーナーをしているコミュニティがある：サーバーの文と、管理の画面への案内を出す
        setFormError(e.message)
        setShowManageLink(true)
      } else {
        setFormError('削除できませんでした。時間をおいて、もう一度お試しください。')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  /**
   * 【何のため】確認の欄を閉じて、入力をすべて元に戻す。
   */
  function handleCancel() {
    setIsConfirming(false)
    setPassword('')
    setPasswordError(undefined)
    setFormError(null)
    setShowManageLink(false)
  }

  return (
    <div className={`${formStyles.section} ${formStyles.dangerSection}`}>
      <h2>アカウントの削除</h2>
      {/* 何が消えて、何が残るかを、削除の前に説明する（docs/screens.md の S-12） */}
      <ul className={styles.deleteNotes}>
        <li>本名、ニックネーム、メールアドレス、パスワードは削除され、元に戻せません。</li>
        <li>参加しているすべてのコミュニティから退会し、開催前のイベントへの申込みはすべてキャンセルされます。</li>
        <li>過去のイベントの申込みの記録は残り、「退会したユーザー」と表示されます。</li>
        <li>オーナーをしているコミュニティがある場合は、すべてアーカイブし、開催前のイベントがなくなるまで削除できません。</li>
      </ul>

      {!isConfirming ? (
        <button type="button" className={formStyles.danger} onClick={() => setIsConfirming(true)}>
          アカウントを削除する
        </button>
      ) : (
        <form className={formStyles.form} onSubmit={handleSubmit} noValidate>
          {formError && (
            <p className={formStyles.formError} role="alert">
              {formError}
              {showManageLink && (
                <>
                  {' '}
                  <Link to="/manage/communities">管理しているコミュニティを見る</Link>
                </>
              )}
            </p>
          )}
          <FormField
            id="account-delete-password"
            label="確認のため、パスワードを入力してください"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            error={passwordError}
          />
          <div className={formStyles.actions}>
            <button type="submit" className={formStyles.danger} disabled={isSubmitting}>
              {isSubmitting ? '削除中…' : '削除を確定する'}
            </button>
            <button type="button" className={formStyles.secondary} onClick={handleCancel} disabled={isSubmitting}>
              やめる
            </button>
          </div>
        </form>
      )}
    </div>
  )
}

// 画面の対応表（src/router.tsx）から読み込めるようにする
export default AccountSettingsPage

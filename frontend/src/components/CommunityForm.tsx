/**
 * 【役割】コミュニティの名前と説明を入力するフォーム（作成と編集で共通）。
 * 【なぜ必要か】作成の画面（S-04）と編集の画面（S-06）は入力欄が同じなので、1つの部品にまとめる。
 * 送信したときの処理（作成の API か、編集の API か）は、呼び出す画面から受け取る。
 */

import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import type { CommunityInput } from '../types/community'
import { toFieldMessages } from '../utils/fieldErrors'
import FormField from './FormField'
import styles from './Form.module.css'
import TextAreaField from './TextAreaField'

type CommunityFormProps = {
  // 入力欄に最初に入れておく値（編集のときは今の値、作成のときは空）
  initialValue: CommunityInput
  // 送信のボタンの文字（「作成する」「保存する」）
  submitLabel: string
  // 入力できない状態にするか（アーカイブ中の編集など）
  disabled?: boolean
  // 送信したときに呼ぶ関数。失敗したときは ApiError を投げる
  onSubmit: (input: CommunityInput) => Promise<void>
}

/**
 * 【何のため】コミュニティの名前と説明の入力欄と、送信のボタンを表示する。
 */
function CommunityForm({ initialValue, submitLabel, disabled = false, onSubmit }: CommunityFormProps) {
  const [name, setName] = useState(initialValue.name)
  const [description, setDescription] = useState(initialValue.description)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldMessages, setFieldMessages] = useState<Record<string, string>>({})

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)
    setFormError(null)
    setFieldMessages({})
    try {
      await onSubmit({ name, description })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'VALIDATION_ERROR') {
        setFieldMessages(toFieldMessages(error.fieldErrors))
      } else if (error instanceof ApiError) {
        setFormError(error.message)
      } else {
        setFormError('通信中にエラーが発生しました。時間をおいて、もう一度お試しください。')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      {formError && (
        <p className={styles.formError} role="alert">
          {formError}
        </p>
      )}
      <FormField
        id="community-name"
        label="コミュニティの名前"
        maxLength={50}
        value={name}
        onChange={(event) => setName(event.target.value)}
        hint="50文字まで"
        error={fieldMessages.name}
        disabled={disabled}
      />
      <TextAreaField
        id="community-description"
        label="説明"
        maxLength={2000}
        value={description}
        onChange={(event) => setDescription(event.target.value)}
        hint="2000文字まで。どんな人が集まるコミュニティかを書いてください。他人の個人情報は書かないでください。"
        error={fieldMessages.description}
        disabled={disabled}
      />
      <div className={styles.actions}>
        <button type="submit" className={styles.submit} disabled={disabled || isSubmitting}>
          {isSubmitting ? '送信中…' : submitLabel}
        </button>
      </div>
    </form>
  )
}

export default CommunityForm

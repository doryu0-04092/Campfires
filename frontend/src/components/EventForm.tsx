/**
 * 【役割】イベントの内容を入力するフォーム（作成と編集で共通）。
 * 【なぜ必要か】作成の画面（S-08）と編集の画面（S-10）は入力欄が同じなので、1つの部品にまとめる。
 * 場所の入力欄の近くには、個人の住所を書かないよう注意書きを出す（要件定義書 5.2）。
 * 参加の承認の方法（メンバー、メンバー以外）も、ここで選ぶ（要件定義書 5.12）。
 * 日時は、この PC の時刻（日本時間）で入力し、API には世界標準時に直して送る。
 */

import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { APPROVAL_LABELS, type EventInput, type MemberApproval, type NonMemberApproval } from '../types/event'
import { toFieldMessages } from '../utils/fieldErrors'
import { fromDateTimeLocal, toDateTimeLocal } from '../utils/formatDateTime'
import FormField from './FormField'
import styles from './Form.module.css'
import SelectField from './SelectField'
import TextAreaField from './TextAreaField'

type EventFormProps = {
  // 入力欄に最初に入れておく値（編集のときは今の値、作成のときは null）
  initialValue: EventInput | null
  // 送信のボタンの文字（「作成する」「保存する」）
  submitLabel: string
  // 送信したときに呼ぶ関数。失敗したときは ApiError を投げる
  onSubmit: (input: EventInput) => Promise<void>
}

/**
 * 【何のため】イベントの入力欄と、送信のボタンを表示する。
 */
function EventForm({ initialValue, submitLabel, onSubmit }: EventFormProps) {
  // 入力中の値。日時の入力欄は「2026-10-10T19:00」の形で持つ
  const [title, setTitle] = useState(initialValue?.title ?? '')
  const [description, setDescription] = useState(initialValue?.description ?? '')
  const [startAt, setStartAt] = useState(initialValue ? toDateTimeLocal(initialValue.startAt) : '')
  const [endAt, setEndAt] = useState(initialValue ? toDateTimeLocal(initialValue.endAt) : '')
  const [deadline, setDeadline] = useState(initialValue ? toDateTimeLocal(initialValue.applicationDeadline) : '')
  const [location, setLocation] = useState(initialValue?.location ?? '')
  // 定員は数だが、入力欄では文字として持ち、送るときに数に変える（空のときを扱いやすくするため）
  const [capacity, setCapacity] = useState(initialValue ? String(initialValue.capacity) : '')
  // 参加の承認の方法。作成のときは、メンバーは自動、メンバー以外は受け付けない（最初の値）
  const [memberApproval, setMemberApproval] = useState<MemberApproval>(initialValue?.memberApproval ?? 'auto')
  const [nonMemberApproval, setNonMemberApproval] = useState<NonMemberApproval>(initialValue?.nonMemberApproval ?? 'closed')

  const [isSubmitting, setIsSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldMessages, setFieldMessages] = useState<Record<string, string>>({})

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)
    setFormError(null)
    setFieldMessages({})
    try {
      await onSubmit({
        title,
        description,
        startAt: fromDateTimeLocal(startAt),
        endAt: fromDateTimeLocal(endAt),
        applicationDeadline: fromDateTimeLocal(deadline),
        location,
        // 空なら 0 を送る（API が「1〜1000人の整数にしてください」のエラーを返す）
        capacity: capacity === '' ? 0 : Number(capacity),
        memberApproval,
        nonMemberApproval,
      })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'VALIDATION_ERROR') {
        setFieldMessages(toFieldMessages(error.fieldErrors))
        setFormError('入力内容に誤りがあります。各項目の下の説明を確認してください。')
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
        id="event-title"
        label="タイトル"
        maxLength={100}
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        hint="100文字まで"
        error={fieldMessages.title}
      />
      <TextAreaField
        id="event-description"
        label="説明"
        maxLength={5000}
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        hint="5000文字まで。イベントの検索の対象になります。"
        error={fieldMessages.description}
      />
      {/* type="datetime-local"：日付と時刻を選べる入力欄 */}
      <FormField
        id="event-start-at"
        label="開始日時"
        type="datetime-local"
        value={startAt}
        onChange={(e) => setStartAt(e.target.value)}
        error={fieldMessages.startAt}
      />
      <FormField
        id="event-end-at"
        label="終了日時"
        type="datetime-local"
        value={endAt}
        onChange={(e) => setEndAt(e.target.value)}
        error={fieldMessages.endAt}
      />
      <FormField
        id="event-deadline"
        label="申込み締め切り日時"
        type="datetime-local"
        value={deadline}
        onChange={(e) => setDeadline(e.target.value)}
        hint="開始日時より前にしてください。締め切りを過ぎると、新しく申し込めなくなり、原則としてキャンセルもできなくなります。"
        error={fieldMessages.applicationDeadline}
      />
      <FormField
        id="event-location"
        label="場所"
        maxLength={200}
        value={location}
        onChange={(e) => setLocation(e.target.value)}
        // 個人の住所を書かないよう、注意書きを出す（要件定義書 5.2）
        hint="自宅の住所など、個人の住所は入力しないでください。イベントの情報は、ログインしていない人も含めて誰でも見られます。"
        error={fieldMessages.location}
      />
      <FormField
        id="event-capacity"
        label="定員"
        type="number"
        min={1}
        max={1000}
        value={capacity}
        onChange={(e) => setCapacity(e.target.value)}
        hint="1〜1000人"
        error={fieldMessages.capacity}
      />
      {/* 参加の承認の方法（要件定義書 5.12）。e.target.value は文字なので、as で決まった値の型として扱う */}
      <SelectField
        id="event-member-approval"
        label="コミュニティのメンバーの参加"
        value={memberApproval}
        onChange={(e) => setMemberApproval(e.target.value as MemberApproval)}
        options={[
          { value: 'auto', label: APPROVAL_LABELS.auto },
          { value: 'manual', label: APPROVAL_LABELS.manual },
        ]}
        hint="自動：申し込むとすぐに参加が決まります。手動：オーナーか運営スタッフが承認したときに、席が確保されます。"
        error={fieldMessages.memberApproval}
      />
      <SelectField
        id="event-non-member-approval"
        label="メンバー以外の参加"
        value={nonMemberApproval}
        onChange={(e) => setNonMemberApproval(e.target.value as NonMemberApproval)}
        options={[
          { value: 'closed', label: APPROVAL_LABELS.closed },
          { value: 'auto', label: APPROVAL_LABELS.auto },
          { value: 'manual', label: APPROVAL_LABELS.manual },
        ]}
        hint="コミュニティに参加していない人も申し込めるようにするかを選びます。手動から自動に変えると、承認待ちの申込みは、申し込んだ順にまとめて承認されます。"
        error={fieldMessages.nonMemberApproval}
      />
      <div className={styles.actions}>
        <button type="submit" className={styles.submit} disabled={isSubmitting}>
          {isSubmitting ? '送信中…' : submitLabel}
        </button>
      </div>
    </form>
  )
}

export default EventForm

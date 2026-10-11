/**
 * 【役割】複数行の入力欄1つ分（項目名、入力欄、補足の文、エラーの文）を表示する部品。
 * 【なぜ必要か】コミュニティやイベントの説明のような、長い文章を入力する欄で使う。
 * 1行の入力欄（FormField）と同じ見た目と、読み上げソフトへの対応にそろえている。
 */

import type { TextareaHTMLAttributes } from 'react'
import styles from './Form.module.css'

type TextAreaFieldProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  id: string
  label: string
  hint?: string
  error?: string
}

/**
 * 【何のため】複数行の入力欄1つ分を表示する。
 */
function TextAreaField({ id, label, hint, error, ...textAreaProps }: TextAreaFieldProps) {
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <textarea
        id={id}
        className={styles.input}
        // 最初の高さ（行数）。入力が長くなったら、利用者が広げられる
        rows={6}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        {...textAreaProps}
      />
      {hint && (
        <p id={hintId} className={styles.hint}>
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className={styles.fieldError}>
          {error}
        </p>
      )}
    </div>
  )
}

export default TextAreaField

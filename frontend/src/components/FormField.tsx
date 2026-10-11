/**
 * 【役割】入力欄1つ分（項目名、入力欄、補足の文、エラーの文）を表示する部品。
 * 【なぜ必要か】入力の画面ごとに同じ形を書くと、見た目や読み上げソフトへの対応がばらばらになるため、1つにまとめる。
 * 項目名と入力欄を id で結び付け、エラーがあるときは aria-invalid で読み上げソフトに伝える（非機能要件 6章のアクセシビリティ）。
 */

import type { InputHTMLAttributes } from 'react'
import styles from './Form.module.css'

/**
 * 【何のため】この部品が受け取る値の形。
 * InputHTMLAttributes は、普通の <input> に渡せる値（type、value、onChange など）すべての形。
 * & で、それに「項目名」「補足」「エラー」を足している。
 */
type FormFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  // 入力欄の id。項目名との結び付けに使う
  id: string
  // 項目名
  label: string
  // 入力欄の下に出す補足の文（なくてもよい）
  hint?: string
  // エラーの文（なければ undefined）
  error?: string
}

/**
 * 【何のため】入力欄1つ分を表示する。
 * { id, label, hint, error, ...inputProps } は、決まった4つを取り出し、残り（type、value など）を inputProps にまとめる書き方。
 */
function FormField({ id, label, hint, error, ...inputProps }: FormFieldProps) {
  // 補足とエラーの文に付ける id。aria-describedby で、入力欄の説明として読み上げてもらう
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  // 2つの id を空白でつなぐ。どちらもなければ undefined にする（filter(Boolean) で空のものを取り除く）
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <input
        id={id}
        className={styles.input}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        // 残りの値（type、value、onChange など）を、そのまま <input> に渡す
        {...inputProps}
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

export default FormField

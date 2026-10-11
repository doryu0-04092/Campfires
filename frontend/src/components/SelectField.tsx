/**
 * 【役割】選択肢から1つを選ぶ入力欄1つ分（項目名、選択欄、補足の文、エラーの文）を表示する部品。
 * 【なぜ必要か】FormField（文字の入力欄）と同じ見た目と、読み上げソフトへの対応で、選択欄を作るため。
 * 項目名と選択欄を id で結び付け、エラーがあるときは aria-invalid で読み上げソフトに伝える（非機能要件 6章）。
 */

import type { SelectHTMLAttributes } from 'react'
import styles from './Form.module.css'

/**
 * 【何のため】この部品が受け取る値の形。
 * SelectHTMLAttributes は、普通の <select> に渡せる値（value、onChange など）すべての形。
 */
type SelectFieldProps = SelectHTMLAttributes<HTMLSelectElement> & {
  // 選択欄の id。項目名との結び付けに使う
  id: string
  // 項目名
  label: string
  // 選択肢の一覧（value：送る値、label：画面に出す文）
  options: { value: string; label: string }[]
  // 選択欄の下に出す補足の文（なくてもよい）
  hint?: string
  // エラーの文（なければ undefined）
  error?: string
}

/**
 * 【何のため】選択欄1つ分を表示する。
 */
function SelectField({ id, label, options, hint, error, ...selectProps }: SelectFieldProps) {
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <select
        id={id}
        className={styles.input}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        {...selectProps}
      >
        {/* 選択肢を1つずつ並べる */}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
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

export default SelectField

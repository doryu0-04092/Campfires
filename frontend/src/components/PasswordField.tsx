/**
 * 【役割】パスワードの入力欄に、「パスワードを表示する」のチェックボックスを付けた部品。
 * 【なぜ必要か】パスワードは ● で隠して入力するため、打ち間違いに気づきにくい。
 * チェックを入れると入力した文字が見えるようにして、何を入力したかを確かめられるようにする。
 * 登録とログインの画面で使う。
 */

import { useState, type InputHTMLAttributes } from 'react'
import FormField from './FormField'
import styles from './Form.module.css'

/**
 * 【何のため】この部品が受け取る値の形。FormField と同じ値を受け取る（type は、この部品が決めるので受け取らない）。
 * Omit<型, 'type'> は「その型から type を除いた形」という意味。
 */
type PasswordFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & {
  id: string
  label: string
  hint?: string
  error?: string
}

/**
 * 【何のため】パスワードの入力欄と、「パスワードを表示する」のチェックボックスを表示する。
 */
function PasswordField(props: PasswordFieldProps) {
  // パスワードを見える状態にしているかどうか。最初は隠している
  const [isVisible, setIsVisible] = useState(false)

  return (
    <div>
      {/* 見える状態なら type="text"（そのまま表示）、隠す状態なら type="password"（● で隠す） */}
      <FormField {...props} type={isVisible ? 'text' : 'password'} />
      <label className={`${styles.checkbox} ${styles.note}`}>
        <input
          type="checkbox"
          checked={isVisible}
          onChange={(event) => setIsVisible(event.target.checked)}
          // aria-controls：このチェックボックスが、どの入力欄を操作するかを読み上げソフトに伝える
          aria-controls={props.id}
        />
        パスワードを表示する
      </label>
    </div>
  )
}

export default PasswordField

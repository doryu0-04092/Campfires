/**
 * 【役割】検索欄（入力欄と「検索」ボタン）の部品。
 * 【なぜ必要か】入力中の文字を覚えておく処理と、検索ボタンを押したときの処理を、画面から切り分けるため。
 * 入力のルール（100文字まで）は、要件定義書 5.8 に合わせている。
 */

// useState：部品の中で、変わる値（ここでは入力中の文字）を覚えておくための関数
// FormEvent：フォームを送信したときの出来事（イベント）の型
import { useState, type FormEvent } from 'react'
import styles from './SearchForm.module.css'

/**
 * 【何のため】この部品が受け取る値の形を決める。
 */
type SearchFormProps = {
  // 入力欄に最初に入れておく文字（URL の q の値）
  initialQuery: string
  // 検索ボタンが押されたときに呼ぶ関数。入力された文字を渡す
  onSearch: (query: string) => void
}

/**
 * 【何のため】検索欄を表示し、検索ボタンが押されたら、入力された文字を呼び出し元に渡す。
 */
function SearchForm({ initialQuery, onSearch }: SearchFormProps) {
  // query：今入力されている文字。setQuery：それを変えるための関数
  const [query, setQuery] = useState(initialQuery)

  /**
   * 【何のため】検索ボタンが押された（フォームが送信された）ときの処理。
   */
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    // フォームを送信すると、ブラウザはページ全体を読み込み直そうとする。それを止める
    event.preventDefault()
    // 前後の空白を取り除いてから渡す
    onSearch(query.trim())
  }

  return (
    // role="search"：読み上げソフトに「ここは検索の場所」と伝える
    <form className={styles.form} role="search" onSubmit={handleSubmit}>
      {/* label と入力欄を htmlFor と id で結び付けると、読み上げソフトが入力欄の名前を読める。
          見た目では隠している（visuallyHidden） */}
      <label htmlFor="event-search" className={styles.visuallyHidden}>
        イベントを検索
      </label>
      <input
        id="event-search"
        // type="search"：検索用の入力欄。ブラウザによっては、入力を消す × ボタンが付く
        type="search"
        className={styles.input}
        placeholder="キーワードで探す（例：Java 初心者）"
        // 入力欄には、覚えている文字（query）を表示する
        value={query}
        // 文字が入力されるたびに、覚えている文字を更新する
        onChange={(event) => setQuery(event.target.value)}
        // 100文字より多くは入力できないようにする
        maxLength={100}
      />
      <button type="submit" className={styles.button}>
        検索
      </button>
    </form>
  )
}

// イベント一覧の画面（src/pages/EventListPage.tsx）から読み込めるようにする
export default SearchForm

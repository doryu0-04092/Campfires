/**
 * 【役割】ページの切り替え（「前へ」、ページ番号、「次へ」）を表示する部品。
 * 【なぜ必要か】一覧を30件ずつに分けて表示するため（要件定義書 5.8）。
 * ほかの一覧の画面でも使い回せるように、部品として分けている。
 */

// Link：ページ全体を読み込み直さずに、別の画面へ移動するためのリンク
import { Link } from 'react-router'
import styles from './Pagination.module.css'

// 一度に並べるページ番号の数の上限。これより多いときは、今のページの前後だけを並べる
const MAX_VISIBLE_PAGES = 10

/**
 * 【何のため】この部品が受け取る値の形を決める。
 */
type PaginationProps = {
  // 今のページ番号
  page: number
  // 全部で何ページあるか
  totalPages: number
  // ページ番号から、そのページの URL を作る関数（検索の文字を URL に残すため、呼び出し元に作ってもらう）
  buildHref: (page: number) => string
}

/**
 * 【何のため】ページの切り替えを表示する。1ページしかないときは、何も表示しない。
 */
function Pagination({ page, totalPages, buildHref }: PaginationProps) {
  // 1ページしかなければ、切り替えは不要。null を返すと、何も表示されない
  if (totalPages <= 1) {
    return null
  }

  // 並べるページ番号の範囲を決める。今のページが真ん中あたりに来るようにする。
  // Math.max は大きいほう、Math.min は小さいほうを選ぶ関数
  const start = Math.max(1, Math.min(page - Math.floor(MAX_VISIBLE_PAGES / 2), totalPages - MAX_VISIBLE_PAGES + 1))
  const end = Math.min(totalPages, start + MAX_VISIBLE_PAGES - 1)
  // start から end までの番号の一覧を作る（例：start=1, end=3 → [1, 2, 3]）
  const pageNumbers = Array.from({ length: end - start + 1 }, (_, index) => start + index)

  return (
    // aria-label：読み上げソフトに「ページの切り替え」の場所だと伝える
    <nav className={styles.pagination} aria-label="ページの切り替え">
      {/* 「前へ」：1ページ目のときは押せない（リンクではなく、ただの文字にする） */}
      {page > 1 ? (
        <Link to={buildHref(page - 1)} className={styles.item}>前へ</Link>
      ) : (
        <span className={`${styles.item} ${styles.disabled}`}>前へ</span>
      )}

      {/* ページ番号を並べる。今のページは、リンクにせず目立たせる */}
      {pageNumbers.map((number) =>
        number === page ? (
          // aria-current="page"：読み上げソフトに「今のページ」だと伝える
          <span key={number} className={`${styles.item} ${styles.current}`} aria-current="page">
            {number}
          </span>
        ) : (
          <Link key={number} to={buildHref(number)} className={styles.item}>
            {number}
          </Link>
        ),
      )}

      {/* 「次へ」：最後のページのときは押せない */}
      {page < totalPages ? (
        <Link to={buildHref(page + 1)} className={styles.item}>次へ</Link>
      ) : (
        <span className={`${styles.item} ${styles.disabled}`}>次へ</span>
      )}
    </nav>
  )
}

// イベント一覧の画面などから読み込めるようにする
export default Pagination

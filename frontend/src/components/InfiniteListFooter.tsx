/**
 * 【役割】少しずつ読み込む一覧の、一番下に置く部品。
 * 【なぜ必要か】お知らせ、イベントの検索、コミュニティの検索の3つの画面で、同じものを置くため。
 * - 読み込み中の表示と、読み込みに失敗したときの表示
 * - 一覧の一番下の目印（これが画面に見えたら、次の 30件を読み込む）
 * - スクロールで読み込めないときのための「さらに読み込む」ボタン
 */

import type { RefObject } from 'react'
import styles from './List.module.css'
import LoadError from './LoadError'

/**
 * 【何のため】この部品が受け取る値の形。useInfiniteList が返す値の一部。
 */
type InfiniteListFooterProps = {
  list: {
    isLoading: boolean
    error: unknown
    hasMore: boolean
    isFirstLoaded: boolean
    loadMore: () => Promise<void>
  }
  // 一覧の一番下の目印。React の決まりで、目印（ref）は、ほかの値とは別に受け取る
  sentinelRef: RefObject<HTMLDivElement | null>
}

/**
 * 【何のため】一覧の一番下に置くものを表示する。
 */
function InfiniteListFooter({ list, sentinelRef }: InfiniteListFooterProps) {
  return (
    <>
      {list.isLoading && <p className={styles.message}>読み込み中…</p>}
      {list.error !== undefined && <LoadError error={list.error} />}
      {/* 一覧の一番下の目印。これが画面に見えたら、次の 30件を読み込む */}
      <div ref={sentinelRef} />
      {/* スクロールで読み込めないときのためのボタン */}
      {list.hasMore && list.isFirstLoaded && !list.isLoading && list.error === undefined && (
        <div className={styles.actions}>
          <button type="button" className={styles.button} onClick={() => void list.loadMore()}>
            さらに読み込む
          </button>
        </div>
      )}
    </>
  )
}

export default InfiniteListFooter

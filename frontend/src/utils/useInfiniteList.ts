/**
 * 【役割】「最初に 30件を表示し、下までスクロールしたら次の 30件を足す」一覧の、共通の仕組み（フック）。
 * 【なぜ必要か】お知らせ、イベントの検索、コミュニティの検索の3つの画面で、同じ動きをさせるため（要件定義書 5.8、5.10）。
 * API には page を 1, 2, 3… と増やしながら問い合わせ、届いた分を一覧の後ろに足していく。
 * 一覧の一番下に置いた目印（sentinelRef）が画面に見えたら、次の分を読み込む。
 */

import { useCallback, useEffect, useRef, useState } from 'react'
import type { PageInfo } from '../types/api'

/**
 * 【何のため】useInfiniteList が覚えておく値の形。
 */
type ListState<T, R> = {
  // どの条件（検索の文字など）の一覧か。条件が変わったら、最初から読み込み直す
  key: string
  // これまでに読み込んだ行
  items: T[]
  // 次に読み込むページ番号
  nextPage: number
  // まだ読み込んでいない行があるか
  hasMore: boolean
  // 最後に届いた返事（件数などを表示するために使う）
  last: R | undefined
  // 読み込みに失敗したときのエラー
  error: unknown
}

/**
 * 【何のため】条件が変わったときの、最初の状態を作る。
 */
function initialState<T, R>(key: string): ListState<T, R> {
  return { key, items: [], nextPage: 1, hasMore: true, last: undefined, error: undefined }
}

/**
 * 【何のため】少しずつ読み込む一覧を管理する。
 * @param key 一覧の条件を表す文字（例："events:Java"）。これが変わると、最初から読み込み直す
 * @param fetchPage 指定したページを読み込む関数
 */
export function useInfiniteList<T, R extends PageInfo & { items: T[] }>(
  key: string,
  fetchPage: (page: number) => Promise<R>,
) {
  const [stored, setStored] = useState<ListState<T, R>>(() => initialState(key))
  // 条件が変わっていたら、覚えている一覧は古い条件のものなので、最初の状態として扱う
  const state = stored.key === key ? stored : initialState<T, R>(key)

  // 読み込みの途中かどうか（同時に2回読み込まないようにするため。state は描き直すまで新しくならないので、ref で確かめる）
  const loadingRef = useRef(false)
  const [isLoading, setIsLoading] = useState(false)
  // 今の条件。読み込みの途中で条件が変わったときに、古い条件の結果を捨てるために使う
  const keyRef = useRef(key)
  useEffect(() => {
    keyRef.current = key
  }, [key])

  /**
   * 【何のため】次のページを読み込んで、一覧の後ろに足す。
   */
  const loadMore = useCallback(async () => {
    if (loadingRef.current || !state.hasMore || state.error !== undefined) {
      return
    }
    loadingRef.current = true
    setIsLoading(true)
    const requestKey = key
    try {
      const result = await fetchPage(state.nextPage)
      // 読み込みの途中で条件が変わっていたら、この結果は捨てる
      if (keyRef.current !== requestKey) {
        return
      }
      setStored({
        key: requestKey,
        items: [...state.items, ...result.items],
        nextPage: result.page + 1,
        hasMore: result.page < result.totalPages,
        last: result,
        error: undefined,
      })
    } catch (error) {
      if (keyRef.current === requestKey) {
        setStored({ ...state, error })
      }
    } finally {
      loadingRef.current = false
      setIsLoading(false)
    }
  }, [key, fetchPage, state])

  // 一覧の一番下に置く目印。これが画面に見えたら、次の分を読み込む
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 目印が見えたら、次の分を読み込む。
  // IntersectionObserver は、ブラウザに備わっている「要素が画面に見えたかどうか」を知らせてくれる道具。
  // 画面を開いた直後や条件が変わった直後は一覧が空なので、目印がすぐに見え、最初の 30件もこの仕組みで読み込まれる
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || !state.hasMore || state.error !== undefined) {
      return
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) {
        void loadMore()
      }
    })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [loadMore, state.hasMore, state.error])

  /**
   * 【何のため】読み込み済みの行を、画面の中で書き換える（例：お知らせを既読の表示にする）。
   */
  const updateItems = useCallback(
    (update: (items: T[]) => T[]) => {
      setStored((previous) => (previous.key === key ? { ...previous, items: update(previous.items) } : previous))
    },
    [key],
  )

  return {
    items: state.items,
    last: state.last,
    hasMore: state.hasMore,
    error: state.error,
    // 最初の読み込みが終わったかどうか（「まだありません」などを出すかどうかに使う）
    isFirstLoaded: state.last !== undefined,
    isLoading,
    loadMore,
    sentinelRef,
    updateItems,
  }
}

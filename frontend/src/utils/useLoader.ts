/**
 * 【役割】画面を開いたときに API からデータを読み込む、共通の仕組み（フック）。
 * 【なぜ必要か】多くの画面で「読み込み中 → 成功 → 失敗」の同じ流れを書くことになるため、1か所にまとめる。
 * 読み込みの途中で条件（ページ番号など）が変わったときは、古い通信を取りやめる。
 * 操作（承認、除名など）のあとに、読み込み直す（reload）こともできる。
 */

import { useEffect, useRef, useState } from 'react'

/**
 * 【何のため】useLoader が返す値の形。
 */
type LoaderResult<T> = {
  // 読み込み中かどうか
  isLoading: boolean
  // 読み込んだデータ。まだないときや失敗したときは undefined
  data: T | undefined
  // 失敗したときのエラー。成功したときは undefined
  error: unknown
  // もう一度読み込む
  reload: () => void
}

/**
 * 【何のため】データを読み込み、その状態を返す。
 * @param key 読み込みの条件を表す文字（例："community:3:page:2"）。これが変わると、読み込み直す
 * @param load データを読み込む関数。通信を取りやめるための合図（signal）を受け取る
 */
export function useLoader<T>(key: string, load: (signal: AbortSignal) => Promise<T>): LoaderResult<T> {
  // 何回読み込み直したか。reload で 1 増やすと、下の fullKey が変わって読み込み直しが起きる
  const [version, setVersion] = useState(0)
  // 最後に届いた結果と、それがどの条件のものか
  const [result, setResult] = useState<{ key: string; data?: T; error?: unknown } | null>(null)

  // 読み込む関数は、画面を描き直すたびに新しく作られるので、最新のものを入れ物（ref）にしまっておく。
  // useRef は、画面を描き直しても中身が消えない入れ物。中身を変えても、画面は描き直されない
  const loadRef = useRef(load)
  useEffect(() => {
    loadRef.current = load
  })

  // 条件と、読み込み直した回数を合わせた文字。これが変わったら読み込む
  const fullKey = `${key}#${version}`

  useEffect(() => {
    const controller = new AbortController()
    loadRef
      .current(controller.signal)
      .then((data) => setResult({ key: fullKey, data }))
      .catch((error: unknown) => {
        // 自分で取りやめた通信のエラーは無視する
        if (error instanceof DOMException && error.name === 'AbortError') {
          return
        }
        setResult({ key: fullKey, error })
      })
    return () => controller.abort()
  }, [fullKey])

  // 届いている結果が今の条件のものでなければ、読み込み中とみなす
  const isLoading = result === null || result.key !== fullKey
  return {
    isLoading,
    data: isLoading ? undefined : result?.data,
    error: isLoading ? undefined : result?.error,
    // (v) => v + 1 は「今の値に 1 を足す」という書き方
    reload: () => setVersion((v) => v + 1),
  }
}

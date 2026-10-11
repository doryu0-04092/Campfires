/**
 * 【役割】画面の枠（Layout）から、中の各画面へ渡す値の形と、それを受け取る関数（フック）。
 * 【なぜ必要か】お知らせの画面で既読にしたとき、画面上部の未読の件数を、すぐに取り直してもらうため。
 * React Router の Outlet には、中の画面へ値を渡す仕組み（context）があるので、それを使う。
 */

import { useOutletContext } from 'react-router'

/**
 * 【何のため】Layout から中の画面へ渡す値の形。
 */
export type LayoutContext = {
  // 画面上部の未読の件数を、取り直す
  refreshUnreadCount: () => void
}

/**
 * 【何のため】Layout から渡された値を受け取る。
 * useOutletContext は、親の <Outlet context={...} /> に渡した値を読み出す関数。
 */
export function useLayoutContext(): LayoutContext {
  return useOutletContext<LayoutContext>()
}

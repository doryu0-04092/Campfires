/**
 * 【役割】ログインが必要な画面を守る部品。
 * 【なぜ必要か】ログインしていない人が、ログインが必要な画面（アカウント設定、管理の画面など）に来たとき、
 * ログインの画面へ移動させるため（docs/screens.md 5章）。
 * ログインしたあとは、元の画面に戻れるように、来た場所を覚えておく。
 *
 * ただし、これは使いやすさのための仕組み。本当の権限のチェックはバックエンドで行う（技術構成 5章）。
 */

import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '../auth/useAuth'

/**
 * 【何のため】ログインしていれば中の画面（Outlet）を表示し、していなければログインの画面へ移動する。
 * src/router.tsx で、ログインが必要な画面をこの部品の下にまとめている。
 */
function RequireAuth() {
  const { state } = useAuth()
  // 今の画面の場所（URL）。ログインのあとに戻ってくるために使う
  const location = useLocation()

  // ログインしているか確かめている途中は、何も表示しない（一瞬ログインの画面へ移動してしまうのを防ぐ）
  if (state.status === 'loading') {
    return <p>読み込み中…</p>
  }

  // ログインしていなければ、ログインの画面へ移動する。
  // state={{ from: location }} で、どこから来たかをログインの画面に伝える
  if (state.status === 'guest') {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  // ログインしていれば、中の画面をそのまま表示する
  return <Outlet />
}

export default RequireAuth

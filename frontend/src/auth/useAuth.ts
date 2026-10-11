/**
 * 【役割】ログイン状態を読み出すための関数（フック）。
 * 【なぜ必要か】画面の部品から「const { state, login, logout } = useAuth()」の1行で、
 * ログイン状態とログイン・ログアウトの操作を使えるようにするため。
 * use で始まる関数は「フック」と呼ばれ、React の部品の中でだけ使える決まりになっている。
 */

import { useContext } from 'react'
import { AuthContext, type AuthContextValue } from './authContext'

/**
 * 【何のため】ログイン状態と、ログイン・ログアウトの操作を返す。
 * AuthProvider の外で使われたときは、作り間違いなので、すぐに気づけるようにエラーにする。
 */
export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (value === null) {
    throw new Error('useAuth は AuthProvider の中で使ってください。')
  }
  return value
}

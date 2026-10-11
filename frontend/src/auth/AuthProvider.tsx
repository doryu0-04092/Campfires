/**
 * 【役割】ログイン状態を管理し、画面全体に配る部品。
 * 【なぜ必要か】画面を開いたときにログインしているかを確かめ、その結果と、ログイン・ログアウトの操作を、
 * 画面のどこからでも使えるようにするため（Context の中身を入れる係）。
 * src/main.tsx で、アプリ全体をこの部品で包んでいる。
 */

import { useEffect, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import type { DeleteMeRequest, LoginRequest, SignupRequest, UpdateMeRequest } from '../types/account'
import { AuthContext, type AuthState } from './authContext'

/**
 * 【何のため】この部品が受け取る値の形。children は「この部品で包んだ中身」のこと。
 */
type AuthProviderProps = {
  children: ReactNode
}

/**
 * 【何のため】ログイン状態を管理し、包んだ中身（children）に配る。
 */
function AuthProvider({ children }: AuthProviderProps) {
  // 今のログイン状態。最初は「確かめている途中」
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  // 画面を開いたときに1回だけ、ログインしているかを確かめる。
  // 最後の [] は「最初に1回だけ実行する」という指定
  useEffect(() => {
    // この画面がまだ表示されているかどうか。閉じたあとに結果が届いても、状態を書き換えないようにする
    let active = true

    // ① CSRF トークンを受け取る → ② 自分の情報を取得する、の順に行う
    authApi
      .fetchCsrfToken()
      .then(() => authApi.fetchMe())
      .then((me) => {
        if (active) {
          setState(me ? { status: 'authenticated', me } : { status: 'guest' })
        }
      })
      // 通信に失敗したときは、ログインしていないものとして扱う
      .catch(() => {
        if (active) {
          setState({ status: 'guest' })
        }
      })

    return () => {
      active = false
    }
  }, [])

  /**
   * 【何のため】ログインする。成功したら、ログイン状態を「ログインしている」にする。
   * 失敗したときは ApiError がそのまま呼び出し元（ログインの画面）に伝わる。
   */
  async function login(request: LoginRequest) {
    const me = await authApi.login(request)
    // ログインするとサーバーが CSRF トークンを作り直すことがあるので、受け取り直す（Spring Security の動き）
    await authApi.fetchCsrfToken()
    setState({ status: 'authenticated', me })
  }

  /**
   * 【何のため】ログアウトする。ログイン状態を「ログインしていない」にする。
   */
  async function logout() {
    await authApi.logout()
    await authApi.fetchCsrfToken()
    setState({ status: 'guest' })
  }

  /**
   * 【何のため】アカウントを登録する。成功したら、ログインした状態にする（登録の API がログインもしてくれる）。
   */
  async function signup(request: SignupRequest) {
    const me = await authApi.signup(request)
    await authApi.fetchCsrfToken()
    setState({ status: 'authenticated', me })
  }

  /**
   * 【何のため】ニックネームを変更する。成功したら、覚えている自分の情報も新しくする（画面上部の表示が変わる）。
   */
  async function updateMe(request: UpdateMeRequest) {
    const me = await authApi.updateMe(request)
    setState({ status: 'authenticated', me })
  }

  /**
   * 【何のため】アカウントを削除する。成功したら、ログインしていない状態にする。
   */
  async function deleteAccount(request: DeleteMeRequest) {
    await authApi.deleteMe(request)
    await authApi.fetchCsrfToken()
    setState({ status: 'guest' })
  }

  return (
    // AuthContext.Provider で包むと、中のすべての部品が、value に入れた値を読めるようになる
    <AuthContext.Provider value={{ state, login, logout, signup, updateMe, deleteAccount }}>
      {children}
    </AuthContext.Provider>
  )
}

export default AuthProvider

/**
 * 【役割】ログイン状態を画面全体で共有するための「入れ物」（Context）と、その中身の形を決めるファイル。
 * 【なぜ必要か】ログインしているかどうかは、画面上部のメニュー、各画面のボタンなど、多くの場所で必要になる。
 * 部品から部品へ値を1つずつ渡していくと大変なので、React の Context という仕組みで、
 * 画面のどこからでも同じ値を読めるようにする。
 *
 * 中身を入れるのは AuthProvider.tsx、読み出すのは useAuth.ts。
 * 部品（AuthProvider）と、部品でないもの（この Context）を別のファイルに分けているのは、
 * 開発中に画面を書き換えたとき、ページ全体を読み込み直さずに反映する機能をうまく働かせるため。
 */

import { createContext } from 'react'
import type { DeleteMeRequest, LoginRequest, Me, SignupRequest, UpdateMeRequest } from '../types/account'

/**
 * 【何のため】ログイン状態の形。次の3つのどれか。
 * - loading：確かめている途中（画面を開いた直後）
 * - guest：ログインしていない
 * - authenticated：ログインしている（自分の情報 me を持つ）
 */
export type AuthState =
  | { status: 'loading' }
  | { status: 'guest' }
  | { status: 'authenticated'; me: Me }

/**
 * 【何のため】Context に入れる中身の形。ログイン状態と、ログイン・ログアウトの操作をまとめて渡す。
 */
export type AuthContextValue = {
  // 今のログイン状態
  state: AuthState
  // ログインする。失敗したときは ApiError を投げる
  login: (request: LoginRequest) => Promise<void>
  // ログアウトする。beforeGuest を渡すと、ログインしていない状態にする直前に呼ぶ（画面の移動に使う。AuthProvider の becomeGuest）
  logout: (beforeGuest?: () => void | Promise<void>) => Promise<void>
  // アカウントを登録する。成功したら、ログインした状態になる。失敗したときは ApiError を投げる
  signup: (request: SignupRequest) => Promise<void>
  // ニックネームを変更する。失敗したときは ApiError を投げる
  updateMe: (request: UpdateMeRequest) => Promise<void>
  // アカウントを削除する。成功したら、ログインしていない状態になる。失敗したときは ApiError を投げる。
  // beforeGuest は logout と同じ
  deleteAccount: (request: DeleteMeRequest, beforeGuest?: () => void | Promise<void>) => Promise<void>
}

// ログイン状態の入れ物。最初の値は null（AuthProvider の外では使えない、という意味）
export const AuthContext = createContext<AuthContextValue | null>(null)

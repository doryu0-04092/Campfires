/**
 * 【役割】ログイン（とアカウント登録）のあとに戻る画面を、決めた形で渡し・受け取るための道具。
 * 【なぜ必要か】どの画面から「ログイン」「アカウント登録」「申し込む」を押しても、ログインのあとに、元の画面に戻れるようにするため。
 * 画面の移動のときに、React Router の state（URL には出ない、画面の移動に付けて渡すデータ）に、元の場所を入れて渡す。
 * state はこのサイトの中でだけ作られる（外のサイトから作れない）ので、ほかのサイトへ飛ばされる心配はない（オープンリダイレクトの対策）。
 */
import type { Location } from 'react-router'

/** 画面の移動に付けて渡すデータの形（from：元の場所） */
export type ReturnToState = { from?: Location }

/**
 * 【何のため】ログイン・アカウント登録の画面へ移るときに付ける state を作る。
 * いまログイン・登録の画面にいるとき（2つの画面を行き来したとき）は、最初の元の場所を、そのまま引き継ぐ。
 */
export function returnToState(location: Location): ReturnToState {
  if (location.pathname === '/login' || location.pathname === '/signup') {
    return { from: (location.state as ReturnToState | null)?.from }
  }
  return { from: location }
}

/**
 * 【何のため】ログイン・アカウント登録のあとに戻る URL を返す。検索の言葉（? より後ろ）も残す。
 * 元の場所がなければ、トップ（/）。パスが / で始まらない値は使わない（念のため、このサイトの中だけにする）
 */
export function returnToPath(location: Location): string {
  const from = (location.state as ReturnToState | null)?.from
  if (!from || !from.pathname.startsWith('/') || from.pathname.startsWith('//')) {
    return '/'
  }
  return `${from.pathname}${from.search ?? ''}${from.hash ?? ''}`
}

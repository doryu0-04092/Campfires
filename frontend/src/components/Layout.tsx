/**
 * 【役割】すべての画面に共通する「枠」（画面上部のメニュー、左側のメニュー、画面下部のリンク）を表示する部品。
 * 【なぜ必要か】メニューを画面ごとに書くと、同じコードがいくつもできて直し忘れが起きる。
 * 枠を1か所にまとめ、右側に各画面の中身を差し込む形にするため。
 * 配置は docs/screens.md の「4. 画面の配置」に合わせている。
 */

// Link：ページ全体を読み込み直さずに、別の画面へ移動するためのリンク
// Outlet：URL に合った画面の中身を、ここに差し込むための目印
// ScrollRestoration：画面を移動したとき、スクロールの位置を一番上に戻す（「戻る」のときは元の位置に戻す）
// useNavigate：プログラムの中から、別の画面へ移動するための関数
// useLocation：今の画面の場所（URL）を読み出す関数
import { Link, Outlet, ScrollRestoration, useLocation, useNavigate } from 'react-router'
import { useState } from 'react'
import { fetchUnreadCount } from '../api/notifications'
// ログイン状態と、ログアウトの操作を読み出す
import { useAuth } from '../auth/useAuth'
import { useLoader } from '../utils/useLoader'
import type { LayoutContext } from './layoutContext'
// 左側のメニュー（ユーザー・コミュニティ管理）
import Sidebar from './Sidebar'
// この部品専用の CSS を読み込む。styles.header のように、クラス名を取り出して使う
import styles from './Layout.module.css'

/**
 * 【何のため】画面の枠を表示し、その真ん中に各画面の中身を表示する。
 */
function Layout() {
  const { state } = useAuth()
  const location = useLocation()
  // 未読の件数を取り直すための番号。お知らせの画面で既読にしたときに 1 増やす
  const [unreadVersion, setUnreadVersion] = useState(0)
  // 未読の件数。画面を移動するたび（URL が変わるたび）と、既読にしたときに取り直す。
  // ログインしていないときは、取りに行かずに 0 とする
  const unread = useLoader(`unread:${state.status}:${location.pathname}:${unreadVersion}`, (signal) =>
    state.status === 'authenticated' ? fetchUnreadCount(signal) : Promise.resolve(0),
  )
  // 中の画面へ渡す値（お知らせの画面が、既読にしたあとに呼ぶ）
  const context: LayoutContext = { refreshUnreadCount: () => setUnreadVersion((v) => v + 1) }

  return (
    // 画面全体を縦に並べる入れ物
    <div className={styles.page}>
      {/* 画面上部のメニュー（全画面共通） */}
      <header className={styles.header}>
        {/* ロゴ：押すとトップのイベント一覧（S-01）に戻る */}
        <Link to="/" className={styles.logo}>
          Campfires
        </Link>
        {/* メニューの中身は、ログイン状態によって変わるので、HeaderMenu に分けている */}
        {/* 取り直している途中も、前の件数を出しておく（数字がちらつかないように） */}
        <HeaderMenu unreadCount={unread.latestData ?? 0} />
      </header>

      {/* 画面の真ん中：左に Sidebar、右に各画面の中身を横に並べる */}
      <div className={styles.body}>
        <Sidebar />
        {/* 画面の中身：URL に合った画面が、この Outlet の場所に表示される。
            context に渡した値は、中の画面から useLayoutContext() で受け取れる */}
        <main className={styles.main}>
          <Outlet context={context} />
        </main>
      </div>

      {/* 画面下部のリンク（全画面共通） */}
      <footer className={styles.footer}>
        <Link to="/privacy">プライバシーポリシー</Link>
        <Link to="/terms">利用規約</Link>
      </footer>

      {/* スクロールの位置の管理。画面には何も表示されない */}
      <ScrollRestoration />
    </div>
  )
}

/**
 * 【何のため】画面上部のメニューを、ログイン状態に合わせて表示する。
 * - 確かめている途中：何も出さない（一瞬「ログイン」が見えてから消える、というちらつきを防ぐため）
 * - ログインしていない：「ログイン」「アカウント登録」
 * - ログインしている：ニックネーム、「お知らせ」「アカウント設定」「ログアウト」
 * アカウント設定は、普段の操作でよく使うものではないので、左側のメニューではなく、ここに置いている。
 */
function HeaderMenu({ unreadCount }: { unreadCount: number }) {
  const { state, logout } = useAuth()
  const navigate = useNavigate()
  // ログアウトの途中かどうか。途中はボタンを押せないようにして、二重に押されるのを防ぐ
  const [isLoggingOut, setIsLoggingOut] = useState(false)

  /**
   * 【何のため】ログアウトのボタンが押されたときの処理。ログアウトしたら、トップの画面に移動する。
   */
  async function handleLogout() {
    setIsLoggingOut(true)
    try {
      await logout()
      navigate('/')
    } catch {
      // 失敗しても画面は壊さず、ボタンを押せる状態に戻す（もう一度押してもらう）
    } finally {
      // finally の中は、成功しても失敗しても必ず実行される
      setIsLoggingOut(false)
    }
  }

  // 確かめている途中は、メニューの場所だけ確保して何も出さない
  if (state.status === 'loading') {
    return <nav className={styles.nav} aria-busy="true" />
  }

  // ログインしていないとき
  if (state.status === 'guest') {
    return (
      // メニューのリンクを横に並べる。見た目は menuButton でボタン型にそろえる
      <nav className={styles.nav}>
        <Link to="/login" className={styles.menuButton}>ログイン</Link>
        <Link to="/signup" className={styles.menuButton}>アカウント登録</Link>
      </nav>
    )
  }

  // ログインしているとき
  return (
    <nav className={styles.nav}>
      {/* 誰としてログインしているかを表示する（ニックネームだけ。本名は出さない） */}
      <span className={styles.userName}>{state.me.nickname} さん</span>
      {/* 未読があれば、件数を出す。aria-label で、読み上げソフトには「お知らせ（未読 2件）」と伝える */}
      <Link
        to="/notifications"
        className={styles.menuButton}
        aria-label={unreadCount > 0 ? `お知らせ（未読 ${unreadCount}件）` : 'お知らせ'}
      >
        お知らせ
        {unreadCount > 0 && <span className={styles.unreadBadge}>{unreadCount}</span>}
      </Link>
      <Link to="/account" className={styles.menuButton}>アカウント設定</Link>
      <button type="button" className={styles.menuButton} onClick={handleLogout} disabled={isLoggingOut}>
        {isLoggingOut ? 'ログアウト中…' : 'ログアウト'}
      </button>
    </nav>
  )
}

// 他のファイル（src/router.tsx）から読み込めるようにする
export default Layout

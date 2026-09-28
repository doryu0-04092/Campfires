/**
 * 【役割】すべての画面に共通する「枠」（画面上部のメニュー、左側のメニュー、画面下部のリンク）を表示する部品。
 * 【なぜ必要か】メニューを画面ごとに書くと、同じコードがいくつもできて直し忘れが起きる。
 * 枠を1か所にまとめ、右側に各画面の中身を差し込む形にするため。
 * 配置は docs/screens.md の「4. 画面の配置」に合わせている。
 */

// Link：ページ全体を読み込み直さずに、別の画面へ移動するためのリンク
// Outlet：URL に合った画面の中身を、ここに差し込むための目印
import { Link, Outlet } from 'react-router'
// 左側のメニュー（ユーザー・コミュニティ管理）
import Sidebar from './Sidebar'
// この部品専用の CSS を読み込む。styles.header のように、クラス名を取り出して使う
import styles from './Layout.module.css'

/**
 * 【何のため】画面の枠を表示し、その真ん中に各画面の中身を表示する。
 */
function Layout() {
  return (
    // 画面全体を縦に並べる入れ物
    <div className={styles.page}>
      {/* 画面上部のメニュー（全画面共通） */}
      <header className={styles.header}>
        {/* ロゴ：押すとトップのイベント一覧（S-01）に戻る */}
        <Link to="/" className={styles.logo}>
          Campfires
        </Link>
        {/* メニューのリンクを横に並べる。見た目は menuButton でボタン型にそろえる */}
        <nav className={styles.nav}>
          <Link to="/login" className={styles.menuButton}>ログイン</Link>
          <Link to="/signup" className={styles.menuButton}>アカウント登録</Link>
          <Link to="/notifications" className={styles.menuButton}>お知らせ</Link>
          {/* アカウント設定は、左側のメニューの「ユーザー」に移した */}
          {/* ログアウトはバックエンドができてから動くようにする。今は押せない状態にしておく */}
          <button type="button" className={styles.menuButton} disabled>
            ログアウト
          </button>
        </nav>
      </header>

      {/* 画面の真ん中：左に Sidebar、右に各画面の中身を横に並べる */}
      <div className={styles.body}>
        <Sidebar />
        {/* 画面の中身：URL に合った画面が、この Outlet の場所に表示される */}
        <main className={styles.main}>
          <Outlet />
        </main>
      </div>

      {/* 画面下部のリンク（全画面共通） */}
      <footer className={styles.footer}>
        <Link to="/privacy">プライバシーポリシー</Link>
        <Link to="/terms">利用規約</Link>
      </footer>
    </div>
  )
}

// 他のファイル（src/router.tsx）から読み込めるようにする
export default Layout

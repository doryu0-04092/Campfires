/**
 * 【役割】画面の左側に表示するメニュー（サイドバー）。
 * 【なぜ必要か】各画面へ移動する入り口を1か所にまとめるため。
 * 「ユーザー」（利用者の画面）と「コミュニティ管理」（オーナー・運営スタッフの画面）の
 * 2つのまとまりに分けて並べる。
 * 並べる画面は docs/screens.md の「4. 画面の配置」に合わせている。
 */

// useState：部品の中で、変わる値（ここではメニューが開いているかどうか）を覚えておくための関数
import { useState } from 'react'
// NavLink：Link と同じく画面を移動するリンク。今開いている画面のリンクかどうかを見分けられる
import { NavLink } from 'react-router'
// この部品専用の CSS
import styles from './Sidebar.module.css'

/**
 * 【何のため】メニューの1項目の形を決める。
 */
type MenuItem = {
  // 移動先の URL
  to: string
  // メニューに表示する文字
  label: string
}

/**
 * 【何のため】メニューの1つのまとまり（見出しと項目の一覧）の形を決める。
 */
type MenuGroup = {
  // まとまりの見出し
  title: string
  // 見出しの下に添える、誰向けのメニューかの説明
  description: string
  // まとまりに入る項目
  items: MenuItem[]
}

// 「ユーザー」のまとまり。番号がなくても開ける利用者の画面を並べる
const userItems: MenuItem[] = [
  { to: '/', label: 'イベント一覧' },                  // S-01
  { to: '/communities', label: 'コミュニティ一覧' },   // S-17
  { to: '/account', label: 'アカウント設定' },         // S-12
]

// 「コミュニティ管理」のまとまり。番号がなくても開ける管理の画面を並べる
const manageItems: MenuItem[] = [
  { to: '/manage/communities', label: '管理しているコミュニティ' },  // S-16
  { to: '/manage/communities/new', label: 'コミュニティ作成' },      // S-04
]

// 開発中だけ表示する項目。番号が必要な画面を、例の番号（1番）で開けるようにする。
// import.meta.env.DEV は、開発用のサーバー（npm run dev）のときだけ true になる値。
// 本番用のファイル（npm run build）では false になるので、この項目は本番に含まれない。
const devUserItems: MenuItem[] = import.meta.env.DEV
  ? [
      { to: '/communities/1', label: 'コミュニティ詳細（例）' },         // S-05
      { to: '/communities/1/events/1', label: 'イベント詳細（例）' },    // S-09
    ]
  : []

const devManageItems: MenuItem[] = import.meta.env.DEV
  ? [
      { to: '/manage/communities/1/edit', label: 'コミュニティ編集（例）' },                   // S-06
      { to: '/manage/communities/1/members', label: 'メンバー管理（例）' },                    // S-07
      { to: '/manage/communities/1/events/new', label: 'イベント作成（例）' },                 // S-08
      { to: '/manage/communities/1/events/1/edit', label: 'イベント編集（例）' },              // S-10
      { to: '/manage/communities/1/events/1/applicants', label: '申込者一覧（例）' },          // S-11
    ]
  : []

// メニュー全体。上に「ユーザー」、下に「コミュニティ管理」を置く。
// 「管理者」としないのは、要件定義書で「管理者」をアプリの運営者の意味で使っているため。
// [...a, ...b] は、2つの一覧をつなげて1つにする書き方
const menuGroups: MenuGroup[] = [
  {
    title: 'ユーザー',
    description: 'コミュニティに参加して使う',
    items: [...userItems, ...devUserItems],
  },
  {
    title: 'コミュニティ管理',
    description: 'オーナー・運営スタッフ向け',
    items: [...manageItems, ...devManageItems],
  },
]

/**
 * 【何のため】左側のメニューを表示する。
 * 画面の幅が狭いとき（スマートフォン）は、「メニュー」ボタンで開いたり閉じたりする。
 */
function Sidebar() {
  // isOpen：スマートフォンでメニューが開いているかどうか。最初は閉じている（false）。
  // setIsOpen：isOpen を変えるための関数。値が変わると、React が画面を描き直す
  const [isOpen, setIsOpen] = useState(false)

  return (
    <aside className={styles.sidebar}>
      {/* スマートフォンのときだけ表示されるボタン（表示の切り替えは CSS で行う） */}
      <button
        type="button"
        className={styles.toggle}
        // 押すたびに、開いている・閉じているを入れ替える（! は「反対にする」という意味）
        onClick={() => setIsOpen(!isOpen)}
        // 読み上げソフトに、メニューが開いているかどうかを伝える
        aria-expanded={isOpen}
      >
        {/* 開いているときは「閉じる」、閉じているときは「メニュー」と表示する */}
        {isOpen ? 'メニューを閉じる' : 'メニュー'}
      </button>

      {/* メニューの本体。開いているときだけ open のクラスを付ける（スマートフォンでの表示に使う） */}
      <nav className={isOpen ? `${styles.menu} ${styles.open}` : styles.menu}>
        {/* まとまりを1つずつ表示する */}
        {menuGroups.map((group) => (
          <section key={group.title} className={styles.group}>
            <h2 className={styles.groupTitle}>{group.title}</h2>
            {/* 誰向けのメニューかを、見出しの下に小さく表示する */}
            <p className={styles.groupDescription}>{group.description}</p>
            <ul className={styles.list}>
              {/* 項目を1つずつ表示する */}
              {group.items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    // end：URL が完全に一致したときだけ「今開いている画面」とみなす。
                    // これがないと、「/」がすべての画面で一致してしまう
                    end
                    // 今開いている画面のリンクだけ、active のクラスを付けて目立たせる
                    className={({ isActive }) =>
                      isActive ? `${styles.link} ${styles.active}` : styles.link
                    }
                    // 項目を押したら、スマートフォンのメニューを閉じる
                    onClick={() => setIsOpen(false)}
                  >
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </nav>
    </aside>
  )
}

// 画面の枠（Layout.tsx）から読み込めるようにする
export default Sidebar

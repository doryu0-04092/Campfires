/**
 * 【役割】画面の左側に表示するメニュー（サイドバー）。
 * 【なぜ必要か】各画面へ移動する入り口を1か所にまとめるため。
 * - 上に「ユーザー」のまとまり（いつも開いている）。利用者の大半は、こちらだけを使う
 * - 少し下げて「オーナー・運営スタッフ用」の管理メニュー。中に「コミュニティ管理」と「イベント管理」がある。
 *   ふだんは使わない人が多いので、最初は閉じておき、ボタンで開け閉めする
 * - 管理メニューの開け閉めの状態は、ブラウザに覚えておき、次に開いたときも同じ状態にする
 *   （開いて使う人が、毎回開き直さなくて済むように）
 * 並べる画面は docs/screens.md の「4. 画面の配置」に合わせている。
 */

// useState：部品の中で、変わる値（ここではメニューが開いているかどうか）を覚えておくための関数
import { useState } from 'react'
// NavLink：Link と同じく画面を移動するリンク。今開いている画面のリンクかどうかを見分けられる
import { NavLink, useLocation } from 'react-router'
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
  // この項目から進んだ先の画面の URL の形（正規表現）。今の URL が当てはまれば、この項目を強調する。
  // 例：「イベント編集」は、選ぶ画面のあとに /manage/communities/1/events/5/edit を開くので、そこでも強調する
  activeFor?: RegExp
}

/**
 * 【何のため】メニューの1つのまとまり（見出しと項目の一覧）の形を決める。
 */
type MenuGroup = {
  // まとまりの見出し
  title: string
  // まとまりに入る項目
  items: MenuItem[]
}

// 「ユーザー」のまとまり（いつも開いている）。
// コミュニティ詳細とイベント詳細は、一覧から開くので、メニューには置かない
const userGroup: MenuGroup = {
  title: 'ユーザー',
  items: [
    { to: '/', label: 'イベント一覧' },                  // S-01
    { to: '/communities', label: 'コミュニティ一覧' },   // S-17
  ],
}

// 管理メニュー（オーナー・運営スタッフ用）の中のまとまり。
// 「管理者」としないのは、要件定義書で「管理者」をアプリの運営者の意味で使っているため。
// どのコミュニティ・どのイベントかを決めないと開けない画面は、先に「選ぶ画面」（S-18、S-19）を開く（Issue #45）
const manageGroups: MenuGroup[] = [
  {
    title: 'コミュニティ管理',
    items: [
      { to: '/manage/communities', label: '管理しているコミュニティ' },  // S-16
      { to: '/manage/communities/new', label: 'コミュニティ作成' },      // S-04
      {
        to: '/manage/select/community-edit',                                // S-18 → S-06
        label: 'コミュニティ編集',
        activeFor: /^\/manage\/communities\/\d+\/edit$/,
      },
      {
        to: '/manage/select/members',                                       // S-18 → S-07
        label: 'メンバー管理',
        activeFor: /^\/manage\/communities\/\d+\/members$/,
      },
    ],
  },
  {
    title: 'イベント管理',
    items: [
      {
        to: '/manage/select/event-create',                                  // S-18 → S-08
        label: 'イベント作成',
        activeFor: /^\/manage\/communities\/\d+\/events\/new$/,
      },
      {
        to: '/manage/select/event-edit',                                    // S-19 → S-10
        label: 'イベント編集',
        activeFor: /^\/manage\/communities\/\d+\/events\/\d+\/edit$/,
      },
      {
        to: '/manage/select/applicants',                                    // S-19 → S-11
        label: '申込者一覧',
        activeFor: /^\/manage\/communities\/\d+\/events\/\d+\/applicants$/,
      },
    ],
  },
]

// 管理メニューの開け閉めの状態を、ブラウザに覚えておくときの名前
const MANAGE_MENU_KEY = 'campfires.sidebar.manageMenuOpen'

/**
 * 【何のため】覚えておいた管理メニューの状態を読む。覚えていなければ、閉じた状態（false）にする。
 * localStorage は「ブラウザに小さな値を覚えておく場所」。ログインの情報ではなく、表示の好みだけを入れる。
 * ブラウザの設定によっては使えないことがあるので、try … catch で失敗しても画面が壊れないようにする。
 */
function loadManageMenuOpen(): boolean {
  try {
    return localStorage.getItem(MANAGE_MENU_KEY) === 'open'
  } catch {
    return false
  }
}

/**
 * 【何のため】管理メニューの状態を、ブラウザに覚えておく。
 */
function saveManageMenuOpen(open: boolean): void {
  try {
    localStorage.setItem(MANAGE_MENU_KEY, open ? 'open' : 'closed')
  } catch {
    // 覚えておけなくても、今の画面での開け閉めはできるので、何もしない
  }
}

/**
 * 【何のため】左側のメニューを表示する。
 * 画面の幅が狭いとき（スマートフォン）は、「メニュー」ボタンで開いたり閉じたりする。
 */
function Sidebar() {
  // isOpen：スマートフォンでメニューが開いているかどうか。最初は閉じている（false）。
  // setIsOpen：isOpen を変えるための関数。値が変わると、React が画面を描き直す
  const [isOpen, setIsOpen] = useState(false)
  // isManageOpen：管理メニューが開いているかどうか。最初の値は、ブラウザに覚えておいた状態。
  // useState(関数) と書くと、最初の1回だけ、その関数で値を決める
  const [isManageOpen, setIsManageOpen] = useState(loadManageMenuOpen)
  // 今の URL。選ぶ画面から進んだ先の画面でも、メニューの項目を強調するために使う
  const { pathname } = useLocation()

  /**
   * 【何のため】管理メニューを開け閉めし、その状態をブラウザに覚えておく。
   */
  function toggleManageMenu() {
    const next = !isManageOpen
    setIsManageOpen(next)
    saveManageMenuOpen(next)
  }

  /**
   * 【何のため】メニューの1つのまとまり（見出しと項目）を表示する。
   */
  function renderGroup(group: MenuGroup) {
    return (
      <section key={group.title} className={styles.group}>
        <h2 className={styles.groupTitle}>{group.title}</h2>
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
                  isActive || item.activeFor?.test(pathname) ? `${styles.link} ${styles.active}` : styles.link
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
    )
  }

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
        {/* 「ユーザー」のまとまり：いつも開いている */}
        {renderGroup(userGroup)}

        {/* 管理メニュー（オーナー・運営スタッフ用）：ボタンで開け閉めする */}
        <div className={styles.manage}>
          <button
            type="button"
            className={styles.manageToggle}
            onClick={toggleManageMenu}
            // 読み上げソフトに、開いているかどうかと、開け閉めする部分（aria-controls の id）を伝える
            aria-expanded={isManageOpen}
            aria-controls="manage-menu"
          >
            {/* ▼ は開いている、▶ は閉じている印。aria-hidden で、読み上げソフトには読ませない */}
            <span aria-hidden="true" className={styles.manageMark}>
              {isManageOpen ? '▼' : '▶'}
            </span>
            オーナー・運営スタッフ用
          </button>
          {/* hidden は「表示しない」という HTML の印。閉じているときは、中の項目を隠す */}
          <div id="manage-menu" hidden={!isManageOpen}>
            {manageGroups.map(renderGroup)}
          </div>
        </div>
      </nav>
    </aside>
  )
}

// 画面の枠（Layout.tsx）から読み込めるようにする
export default Sidebar

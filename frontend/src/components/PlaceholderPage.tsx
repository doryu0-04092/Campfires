/**
 * 【役割】まだ中身を作っていない画面に、仮の表示を出すための部品。
 * 【なぜ必要か】今の段階では、URL で画面が切り替わることだけを確かめたい。
 * 16個の画面それぞれに同じような仮のコードを書かず、この部品を使い回すため。
 * 各画面の中身を作るときに、この部品は使わなくなる。
 */

// Link：ページ全体を読み込み直さずに、別の画面へ移動するためのリンク
import { Link } from 'react-router'

/**
 * 【何のため】この部品が受け取る値（props）の形を決める。
 * type は「データの形」に名前を付ける TypeScript の書き方。
 */
type PlaceholderPageProps = {
  // 画面の ID（docs/screens.md の S-01 など）
  screenId: string
  // 画面の名前
  title: string
  // この画面で作る予定の内容の説明
  description: string
  // 動作確認のために置く、ほかの画面へのリンク（なくてもよいので「?」を付けている）
  links?: { to: string; label: string }[]
}

/**
 * 【何のため】画面の ID、名前、説明と、動作確認用のリンクを表示する。
 * { screenId, title, ... } の書き方で、受け取った値を1つずつ取り出している。
 */
function PlaceholderPage({ screenId, title, description, links = [] }: PlaceholderPageProps) {
  return (
    <section>
      {/* 例：「S-01 コミュニティ一覧」 */}
      <h1>
        {screenId} {title}
      </h1>
      <p>{description}</p>
      <p>（この画面はまだ仮の表示です）</p>

      {/* リンクが1つ以上あるときだけ、一覧を表示する */}
      {links.length > 0 && (
        <ul>
          {/* links の1つずつを、リンクの行に変換して並べる。key は React が行を見分けるための目印 */}
          {links.map((link) => (
            <li key={link.to}>
              <Link to={link.to}>{link.label}</Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

// 各画面のファイル（src/pages）から読み込めるようにする
export default PlaceholderPage

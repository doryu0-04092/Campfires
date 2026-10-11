/**
 * 【役割】URL の page（ページ番号）を扱う関数をまとめたファイル。
 * 【なぜ必要か】一覧の画面ではどれも「?page=2」の形でページ番号を URL に入れるので、読み書きを1か所にまとめる。
 */

/**
 * 【何のため】URL の page の値を、ページ番号として使える数に変える。
 * 数でない、または 1 より小さいときは 1 にする（URL を手で書き換えられても壊れないようにするため）。
 */
export function parsePage(value: string | null): number {
  const page = Number(value ?? '1')
  return Number.isInteger(page) && page >= 1 ? page : 1
}

/**
 * 【何のため】ページ番号から、その一覧の画面の URL を作る（ページの切り替えのリンクに使う）。
 * 例：("/communities", 2) → "/communities?page=2"、("/communities", 1) → "/communities"
 */
export function pageHref(pathname: string, page: number): string {
  return page > 1 ? `${pathname}?page=${page}` : pathname
}

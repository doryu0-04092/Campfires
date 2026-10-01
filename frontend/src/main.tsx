/**
 * 【役割】画面全体の入り口となるファイル。
 * 【なぜ必要か】index.html の <div id="root"> の中に、React で作った画面を表示するため。
 * ブラウザがアプリを開くと、最初にこのファイルが実行される。
 */

// React の開発用チェック機能（StrictMode）を読み込む
import { StrictMode } from 'react'
// React の画面を HTML の中に表示するための関数を読み込む
import { createRoot } from 'react-dom/client'
// ルーター（URL に合わせて画面を切り替える仕組み）を画面に組み込むための部品
import { RouterProvider } from 'react-router'
// アプリ全体に効く CSS を読み込む
import './index.css'
// URL と画面の対応表から作ったルーター
import { router } from './router.tsx'

/**
 * 【何のため】開発中だけ、偽の API（MSW）を動かし始める。
 * 本番では何もしない。本番では本物のバックエンドの API を使うため。
 *
 * async は「途中で待つ処理を含む関数」という印。await で、偽の API の準備ができるまで待つ。
 */
async function enableMocking() {
  // 開発用のサーバー（npm run dev）でなければ、何もせずに終わる
  if (!import.meta.env.DEV) {
    return
  }
  // import(...) を関数のように書くと、必要になったときだけファイルを読み込める。
  // こう書くと、偽の API のコードは本番用のファイルに含まれない
  const { worker } = await import('./mocks/browser')
  // 偽の API を動かし始める。
  // onUnhandledRequest: 'bypass' は「偽の API の一覧にない通信は、そのまま本来の相手に送る」という意味
  await worker.start({ onUnhandledRequest: 'bypass' })
}

// 偽の API の準備が終わってから（then）、画面を表示する。
// 先に画面を表示すると、準備が終わる前に API を呼んでしまい、失敗することがあるため
enableMocking().then(() => {
  // index.html の <div id="root"> を探し、そこにアプリを表示する。
  // 末尾の「!」は「この要素は必ず存在する」と TypeScript に伝える書き方。
  createRoot(document.getElementById('root')!).render(
    // StrictMode：開発中だけ、よくある書き間違いを警告してくれる。本番の動作には影響しない
    <StrictMode>
      {/* RouterProvider：今の URL を見て、対応表のとおりに画面を表示する */}
      <RouterProvider router={router} />
    </StrictMode>,
  )
})

/**
 * 【役割】画面全体の入り口となるファイル。
 * 【なぜ必要か】index.html の <div id="root"> の中に、React で作った画面を表示するため。
 * ブラウザがアプリを開くと、最初にこのファイルが実行される。
 */

// React の開発用チェック機能（StrictMode）を読み込む
import { StrictMode } from 'react'
// React の画面を HTML の中に表示するための関数を読み込む
import { createRoot } from 'react-dom/client'
// アプリ全体に効く CSS を読み込む
import './index.css'
// 画面のいちばん外側の部品（App）を読み込む
import App from './App.tsx'

// index.html の <div id="root"> を探し、そこに App を表示する。
// 末尾の「!」は「この要素は必ず存在する」と TypeScript に伝える書き方。
createRoot(document.getElementById('root')!).render(
  // StrictMode：開発中だけ、よくある書き間違いを警告してくれる。本番の動作には影響しない
  <StrictMode>
    <App />
  </StrictMode>,
)

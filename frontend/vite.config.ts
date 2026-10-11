/**
 * 【役割】開発ツール Vite の設定ファイル。
 * 【なぜ必要か】開発用サーバーの起動や、本番用ファイルの作成（ビルド）の方法を Vite に伝えるため。
 */

// React を Vite で使えるようにする追加機能（プラグイン）を読み込む
import react from '@vitejs/plugin-react'
// 設定を書くための関数を読み込む（書き間違いを TypeScript が教えてくれるようになる）
import { defineConfig } from 'vite'

// 設定の説明：https://vite.dev/config/
export default defineConfig({
  // 使うプラグインの一覧
  plugins: [react()],
  // 開発用サーバー（npm run dev）の設定
  server: {
    // /api で始まる通信を、本物のバックエンド（Spring Boot、ポート 8080）に転送する（プロキシ）。
    // ブラウザから見ると、画面と API が同じ場所（localhost:5173）にあるように見えるので、
    // Cookie（ログインの証明、CSRF トークン）をそのまま使える。
    // 偽の API（MSW）が動いているときは、通信がブラウザの中で答えられるので、ここまで届かない
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})

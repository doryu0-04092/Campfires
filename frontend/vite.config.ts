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
})

/**
 * 【役割】ブラウザの中で偽の API を動かす準備をするファイル。
 * 【なぜ必要か】MSW は、ブラウザの「サービスワーカー」という仕組み（public/mockServiceWorker.js）を使って、
 * 画面から API への通信を途中で受け止める。その仕組みに、偽の API の一覧（handlers）を渡すため。
 */

// setupWorker：ブラウザ用の偽の API を準備する関数
import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

// 偽の API の一覧を渡して準備する。動かし始めるのは src/main.tsx
export const worker = setupWorker(...handlers)

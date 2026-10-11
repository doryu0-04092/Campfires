/**
 * 【役割】偽の API の一覧。分野ごとのファイルに分けた偽の API を、1つにまとめる。
 * 【なぜ必要か】src/mocks/browser.ts に、すべての偽の API をまとめて渡すため。
 * 分野ごとにファイルを分けているのは、API が増えても1つのファイルが長くなりすぎないようにするため。
 * 本物の API ができたら、同じ URL・同じ形の返事を、Spring Boot が返すことになる。
 */

import { authHandlers } from './auth'
import { eventHandlers } from './events'

// ... は「一覧の中身を、ここに並べる」という書き方。2つの一覧をつなげて1つにしている
export const handlers = [...authHandlers, ...eventHandlers]

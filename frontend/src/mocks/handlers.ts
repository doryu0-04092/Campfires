/**
 * 【役割】偽の API の一覧。「この URL にこう聞かれたら、こう返す」という決まりをまとめる。
 * 【なぜ必要か】バックエンドができるまでの間、画面から API を呼んだときに、偽の返事を返すため。
 * 本物の API ができたら、同じ URL・同じ形の返事を、Spring Boot が返すことになる。
 */

// http：URL ごとの返事の決まりを作るための道具
// HttpResponse：返事を作るための道具
// delay：返事を少し遅らせるための道具（本物の通信の待ち時間をまねる）
import { delay, http, HttpResponse } from 'msw'
import { mockEvents } from './data/events'
import { searchEvents } from './searchEvents'

/**
 * 【何のため】偽の API の決まりを並べた一覧。src/mocks/browser.ts から読み込んで使う。
 */
export const handlers = [
  /**
   * イベント一覧・検索の API。
   * 例：GET /api/events?q=Java+初心者&page=2
   */
  http.get('/api/events', async ({ request }) => {
    // 本物の通信に近づけるため、0.3秒待ってから返事をする（読み込み中の表示を確かめられる）
    await delay(300)

    // URL から、検索の文字（q）とページ番号（page）を取り出す
    const url = new URL(request.url)
    // ?? は「左が空（null）なら右を使う」という意味。q がなければ空の文字にする
    const query = url.searchParams.get('q') ?? ''
    // ページ番号を数に変える。数でない、または 1 より小さいときは 1 にする
    const pageParam = Number(url.searchParams.get('page') ?? '1')
    const page = Number.isInteger(pageParam) && pageParam >= 1 ? pageParam : 1

    // 検索のルールどおりに結果を作り、JSON という形式で返す
    return HttpResponse.json(searchEvents(mockEvents, query, page, new Date()))
  }),
]

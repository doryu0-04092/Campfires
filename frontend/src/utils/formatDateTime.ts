/**
 * 【役割】日時を、画面に表示する形の文字に変える関数。
 * 【なぜ必要か】API は日時を世界標準時で返すので、日本時間に直して、読みやすい形にするため
 * （非機能要件 6. 言語と時刻）。どの画面でも同じ表示にそろえるため、1か所にまとめている。
 */

// 日時を文字にする決まり。Intl.DateTimeFormat は、ブラウザに備わっている日時の表示の道具。
// 一度作ったものを使い回すため、関数の外で1回だけ作る
const dateTimeFormatter = new Intl.DateTimeFormat('ja-JP', {
  timeZone: 'Asia/Tokyo',  // 日本時間で表示する
  year: 'numeric',         // 年（例：2026）
  month: 'numeric',        // 月（例：10）
  day: 'numeric',          // 日（例：10）
  weekday: 'short',        // 曜日（例：土）
  hour: '2-digit',         // 時（例：19）
  minute: '2-digit',       // 分（例：00）
})

/**
 * 【何のため】ISO 8601 形式の日時の文字を、日本時間の読みやすい形に変える。
 * 例："2026-10-10T10:00:00.000Z" → "2026/10/10(土) 19:00"
 */
export function formatDateTime(isoString: string): string {
  return dateTimeFormatter.format(new Date(isoString))
}

/**
 * 【何のため】日時の入力欄（<input type="datetime-local">）に入れる形の文字に変える。
 * 入力欄は「2026-10-10T19:00」のような、この PC の時刻（日本なら日本時間）の形を使う。
 * 例："2026-10-10T10:00:00.000Z" → "2026-10-10T19:00"（日本時間の PC の場合）
 */
export function toDateTimeLocal(isoString: string): string {
  const date = new Date(isoString)
  // getTimezoneOffset は、世界標準時との差（分）。日本なら -540（9時間進んでいる）
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
  // toISOString の先頭16文字（"2026-10-10T19:00"）だけを使う
  return local.toISOString().slice(0, 16)
}

/**
 * 【何のため】日時の入力欄の値を、API に送る形（世界標準時の ISO 8601 形式）に変える。
 * 入力が空のときは、空の文字を返す（API が「入力してください」のエラーを返す）。
 * 例："2026-10-10T19:00"（日本時間の PC の場合）→ "2026-10-10T10:00:00.000Z"
 */
export function fromDateTimeLocal(value: string): string {
  if (value === '') {
    return ''
  }
  // 「2026-10-10T19:00」のような値は、new Date がこの PC の時刻として読み取る
  return new Date(value).toISOString()
}

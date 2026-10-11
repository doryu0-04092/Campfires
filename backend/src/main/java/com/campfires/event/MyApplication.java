package com.campfires.event;

import java.time.Instant;

/**
 * 【役割】自分の申込みの状況（docs/api/openapi.yaml の MyApplication）。
 * 【なぜ必要か】イベントの詳細や、自分が申し込んだイベントの一覧で、
 * 「申込み済みか、キャンセル待ちの何番目か」を画面に伝えるため。
 *
 * @param status           applied（申込み済み）か waitlisted（キャンセル待ち）
 * @param waitlistPosition キャンセル待ちの何番目か（1から始まる）。申込み済みなら null
 * @param appliedAt        申し込んだ日時
 */
public record MyApplication(String status, Integer waitlistPosition, Instant appliedAt) {
}

package com.campfires.notification;

/**
 * 【役割】未読のお知らせの件数の返事（docs/api/openapi.yaml の UnreadNotificationCount）。
 * 【なぜ必要か】画面上部の「お知らせ」に、未読の件数を表示するため。
 *
 * @param count 未読の件数
 */
public record UnreadCountResponse(long count) {
}

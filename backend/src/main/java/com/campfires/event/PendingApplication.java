package com.campfires.event;

/**
 * 【役割】承認待ちの申込み1件（だれの申込みか）。
 * 【なぜ必要か】承認の方法を「手動」から「自動」に変えたとき、承認待ちの申込みを順番に承認するため。
 *
 * @param userId 申し込んだ人の番号
 */
public record PendingApplication(long userId) {
}

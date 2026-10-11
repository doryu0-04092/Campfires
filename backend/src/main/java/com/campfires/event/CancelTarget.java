package com.campfires.event;

/**
 * 【役割】取り消す申込みの情報（申込みと、そのイベントの締め切りの状態）。
 * 【なぜ必要か】申込みを取り消した後の処理（繰り上げ、お知らせ）は、
 * 「締め切りを過ぎているか」「申込み済みか、キャンセル待ちか」で変わるため、まとめて読む。
 *
 * @param applicationId  申込みの番号
 * @param eventId        イベントの番号
 * @param userId         申し込んだユーザーの番号
 * @param status         applied（申込み済み）か waitlisted（キャンセル待ち）
 * @param deadlinePassed 申込みの締め切りを過ぎているかどうか
 */
public record CancelTarget(long applicationId, long eventId, long userId, String status, boolean deadlinePassed) {

    /**
     * 【何のため】申込み済み（キャンセル待ちではない）かどうか。
     */
    public boolean isApplied() {
        return "applied".equals(status);
    }
}

package com.campfires.event;

import java.time.Instant;

/**
 * 【役割】単体テストで使う、イベントの行（EventRow）を作る道具。
 * 【なぜ必要か】EventRow は項目が20個あり、テストのたびに全部を書くと読みにくい。
 * テストで気にする項目（定員、申込み済みの人数、承認の方法、締め切り・開始・終了の状態）だけを指定して作れるようにする。
 */
final class EventRowFixtures {

    // テストで使うコミュニティとイベントの番号
    static final long COMMUNITY_ID = 1;
    static final long EVENT_ID = 10;

    private EventRowFixtures() {
    }

    /**
     * 【何のため】開催前・締め切り前のイベントの行を作る。
     */
    static EventRow row(int capacity, long appliedCount, String memberApproval, String nonMemberApproval) {
        return row(capacity, appliedCount, memberApproval, nonMemberApproval, false, false, false);
    }

    /**
     * 【何のため】締め切り・開始・終了の状態も指定して、イベントの行を作る。
     */
    static EventRow row(int capacity, long appliedCount, String memberApproval, String nonMemberApproval,
            boolean deadlinePassed, boolean started, boolean ended) {
        Instant start = Instant.parse("2026-11-03T10:00:00Z");
        return new EventRow(EVENT_ID, COMMUNITY_ID, "テストのコミュニティ", null, "テストのイベント", "説明",
                start, start.plusSeconds(7200), start.minusSeconds(86400), "オンライン", capacity, appliedCount,
                0, 0, memberApproval, nonMemberApproval, started, ended, deadlinePassed,
                Instant.parse("2026-10-01T00:00:00Z"));
    }
}

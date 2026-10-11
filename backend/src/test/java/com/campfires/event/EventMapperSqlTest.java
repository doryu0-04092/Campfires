package com.campfires.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.campfires.MapperTestBase;

/**
 * 【役割】イベントの SQL（EventMapper.xml）を、本物の PostgreSQL で直接確かめるテスト。
 * 【なぜ必要か】検索の点数の付け方と並び順、残りの枠数の数え方、「開催前・終了済み」の境目（終了日時がちょうど今）、
 * 管理しているイベントの絞り込みなど、SQL の中に書いたルールを、API を通さずに細かく確かめるため。
 * データはテストのたびに取り消される（MapperTestBase の @Transactional）。
 */
class EventMapperSqlTest extends MapperTestBase {

    @Autowired
    private EventMapper eventMapper;

    /** 【何のため】イベントの一覧から、番号だけを取り出す。 */
    private static List<Long> ids(List<EventSummary> events) {
        return events.stream().map(EventSummary::id).toList();
    }

    // ---------- 検索 ----------

    /**
     * 【何のため】検索の並び順を確かめる。当てはまった単語の数が多い順、同じなら作成日時の新しい順。
     * タイトルと説明の両方を探し、英字の大文字・小文字は区別しない。終了したイベントは出さず、
     * アーカイブ中のコミュニティのイベントは出す（要件定義書 5.8、5.9）。
     */
    @Test
    void 検索は単語の数が多い順で同じなら新しい順() {
        String word = uniqueWord();
        long owner = insertUser("オーナー");
        long community = insertCommunity(owner);
        long archived = insertCommunity(owner, "アーカイブ中", true, 0);
        // 作成日時：数が小さいほど新しい（何分前に作ったか）
        long two = insertEvent(community, word + " beta", "", 10, 1440, 2880, 3000, 500);
        long newer = insertEvent(community, "タイトル " + word, "", 10, 1440, 2880, 3000, 100);
        long older = insertEvent(community, word + word, "", 10, 1440, 2880, 3000, 200);
        long inDescription = insertEvent(community, "タイトル", "説明に " + word.toUpperCase() + " がある", 10, 1440, 2880,
                3000, 300);
        long inArchived = insertEvent(archived, word, "", 10, 1440, 2880, 3000, 400);
        insertEvent(community, word + " beta", "", 10, -180, -120, -60, 0);
        insertEvent(community, "当てはまらない", "beta", 10, 1440, 2880, 3000, 0);

        List<String> words = List.of(word, "beta");

        // "beta" だけのイベントは、ほかのテストのデータにもあるかもしれないので、word を含むものだけを比べる
        List<Long> found = ids(eventMapper.search(words, 300, 0)).stream()
                .filter(id -> List.of(two, newer, older, inDescription, inArchived).contains(id))
                .toList();
        assertThat(found).containsExactly(two, newer, older, inDescription, inArchived);
        // word だけで検索すれば、ちょうど5件（終了したイベントは数えない）
        assertThat(eventMapper.countSearch(List.of(word))).isEqualTo(5);
        // word だけなら全部1点なので、新しい順（newer → older → inDescription → inArchived → two）。3件目から2件
        assertThat(ids(eventMapper.search(List.of(word), 2, 2))).containsExactly(inDescription, inArchived);
    }

    /**
     * 【何のため】作成日時も同じなら、番号の大きい順に並べることを確かめる（毎回同じ順番になるように）。
     */
    @Test
    void 作成日時も同じなら番号の大きい順() {
        String word = uniqueWord();
        long community = insertCommunity(insertUser("オーナー"));
        long first = insertEvent(community, word, "", 10, 1440, 2880, 3000, 0);
        long second = insertEvent(community, word, "", 10, 1440, 2880, 3000, 0);

        assertThat(ids(eventMapper.search(List.of(word), 30, 0))).containsExactly(second, first);
    }

    /**
     * 【何のため】% や _ を含む単語で検索しても、ふつうの文字として探すことを確かめる（LIKE の「何でも」の意味にならない）。
     */
    @Test
    void パーセントとアンダースコアはふつうの文字として探す() {
        String word = uniqueWord();
        long community = insertCommunity(insertUser("オーナー"));
        long percent = insertEvent(community, word + "%off", "", 10, 1440, 2880, 3000, 0);
        insertEvent(community, word + "xoff", "", 10, 1440, 2880, 3000, 0);
        long underscore = insertEvent(community, word + "_a", "", 10, 1440, 2880, 3000, 0);
        insertEvent(community, word + "ba", "", 10, 1440, 2880, 3000, 0);

        assertThat(ids(eventMapper.search(List.of(word + "%off"), 30, 0))).containsExactly(percent);
        assertThat(ids(eventMapper.search(List.of(word + "_a"), 30, 0))).containsExactly(underscore);
    }

    /**
     * 【何のため】検索していないとき（単語が空）は、開催前のイベントをすべて数えることを確かめる。
     * ほかのテストのデータもあるので、SQL で直接数えた「終了日時が今より後」のイベントの数と比べる。
     */
    @Test
    void 検索していなければ開催前のイベントをすべて数える() {
        long community = insertCommunity(insertUser("オーナー"));
        long upcoming = insertUpcomingEvent(community, 10);
        insertEndedEvent(community);
        long expected = jdbcTemplate.queryForObject("SELECT count(*) FROM events WHERE end_at > now()", Long.class);

        assertThat(eventMapper.countSearch(List.of())).isEqualTo(expected);
        // 1ページ目に出るとは限らない（ほかのテストのイベントもある）ので、作成日時の新しい順の先頭に出ることで確かめる
        assertThat(eventMapper.search(List.of(), 1, 0).get(0).id()).isEqualTo(upcoming);
    }

    /**
     * 【何のため】残りの枠数は「定員 − 申込み済み」で、キャンセル待ち・承認待ち・却下は数えないことを確かめる。
     */
    @Test
    void 残りの枠数は申込み済みだけを数える() {
        String word = uniqueWord();
        long community = insertCommunity(insertUser("オーナー"));
        long event = insertEvent(community, word, "", 5, 1440, 2880, 3000, 0);
        insertApplication(event, insertUser("a"), "applied", 10);
        insertApplication(event, insertUser("b"), "applied", 9);
        insertApplication(event, insertUser("c"), "waitlisted", 8);
        insertApplication(event, insertUser("d"), "pending", 7);
        insertApplication(event, insertUser("e"), "rejected", 6);

        assertThat(eventMapper.search(List.of(word), 30, 0).get(0).remainingSeats()).isEqualTo(3);
        EventRow row = eventMapper.find(community, event);
        assertThat(row.appliedCount()).isEqualTo(2);
        assertThat(row.waitlistCount()).isEqualTo(1);
        assertThat(row.pendingCount()).isEqualTo(1);
    }

    // ---------- コミュニティのイベント ----------

    /**
     * 【何のため】終了日時がちょうど今のイベントは「終了済み」に入ることを確かめる（開催前は「終了日時が今より後」）。
     * 開催前は開始日時の近い順、終了済みは開始日時の新しい順に並べる。
     */
    @Test
    void 終了日時がちょうど今なら終了済み() {
        long community = insertCommunity(insertUser("オーナー"));
        long later = insertEvent(community, "後", "", 10, 60, 120, 180, 0);
        long sooner = insertEvent(community, "先", "", 10, 30, 60, 90, 0);
        long justEnded = insertEvent(community, "ちょうど今終わった", "", 10, -120, -60, 0, 0);
        long longAgo = insertEvent(community, "前に終わった", "", 10, -600, -500, -400, 0);

        assertThat(eventMapper.countCommunityEvents(community, false)).isEqualTo(2);
        assertThat(eventMapper.countCommunityEvents(community, true)).isEqualTo(2);
        assertThat(ids(eventMapper.findCommunityEvents(community, false, 30, 0))).containsExactly(sooner, later);
        assertThat(ids(eventMapper.findCommunityEvents(community, true, 30, 0))).containsExactly(justEnded, longAgo);
        assertThat(ids(eventMapper.findCommunityEvents(community, true, 1, 1))).containsExactly(longAgo);
    }

    /**
     * 【何のため】締め切り・開始・終了が「ちょうど今」のとき、それぞれ「過ぎた」と判断することを確かめる（<= now()）。
     */
    @Test
    void ちょうど今なら過ぎたと判断する() {
        long community = insertCommunity(insertUser("オーナー"));
        long deadlineNow = insertEvent(community, "締め切りが今", "", 10, 0, 60, 120, 0);
        long startNow = insertEvent(community, "開始が今", "", 10, -60, 0, 60, 0);

        EventRow deadline = eventMapper.find(community, deadlineNow);
        assertThat(deadline.deadlinePassed()).isTrue();
        assertThat(deadline.started()).isFalse();
        EventRow start = eventMapper.find(community, startNow);
        assertThat(start.started()).isTrue();
        assertThat(start.ended()).isFalse();
    }

    /**
     * 【何のため】イベントが、指定したコミュニティのものでなければ、見つからない（null）ことを確かめる。
     * ロックも同じ（URL のコミュニティの番号を書き換えて、ほかのコミュニティのイベントを操作できないように）。
     */
    @Test
    void ほかのコミュニティのイベントは見つからない() {
        long owner = insertUser("オーナー");
        long mine = insertCommunity(owner);
        long other = insertCommunity(owner);
        long event = insertUpcomingEvent(other, 10);

        assertThat(eventMapper.find(mine, event)).isNull();
        assertThat(eventMapper.lock(mine, event)).isNull();
        assertThat(eventMapper.lock(other, event)).isEqualTo(event);
    }

    // ---------- 管理しているイベント ----------

    /**
     * 【何のため】管理しているイベントは、オーナーか運営スタッフをしているコミュニティ（アーカイブ中を含む）のものだけで、
     * 自分の立場と承認待ちの人数が入ることを確かめる。
     */
    @Test
    void 管理しているイベントはオーナーと運営スタッフのコミュニティだけ() {
        long me = insertUser("わたし");
        long other = insertUser("ほかの人");
        long owned = insertCommunity(me);
        long staffed = insertCommunity(other, "運営スタッフをしている", true, 0);
        insertMember(staffed, me, "staff");
        long joined = insertCommunity(other);
        insertMember(joined, me, "member");
        long ownedEvent = insertEvent(owned, "オーナー", "", 10, 60, 120, 180, 0);
        long staffedEvent = insertEvent(staffed, "運営スタッフ", "", 10, 30, 60, 90, 0);
        insertUpcomingEvent(joined, 10);
        insertEndedEvent(owned);
        insertApplication(staffedEvent, insertUser("申込み"), "pending", 5);

        assertThat(eventMapper.countManaged(me, false)).isEqualTo(2);
        assertThat(eventMapper.countManaged(me, true)).isEqualTo(1);
        List<ManagedEvent> managed = eventMapper.findManaged(me, false, 30, 0);
        assertThat(managed).extracting(ManagedEvent::id).containsExactly(staffedEvent, ownedEvent);
        assertThat(managed).extracting(ManagedEvent::myRole).containsExactly("staff", "owner");
        assertThat(managed).extracting(ManagedEvent::pendingCount).containsExactly(1L, 0L);
    }

    // ---------- 自分が申し込んだイベント ----------

    /**
     * 【何のため】自分が申し込んだイベントは、開催前のものだけを開始日時の近い順に返し、
     * キャンセル待ちの順番（席の順番を取った順）が入ることを確かめる。
     */
    @Test
    void 自分が申し込んだ開催前のイベントを近い順に返す() {
        long me = insertUser("わたし");
        long community = insertCommunity(insertUser("オーナー"));
        long later = insertEvent(community, "後", "", 1, 60, 300, 360, 0);
        long sooner = insertEvent(community, "先", "", 10, 60, 120, 180, 0);
        long ended = insertEndedEvent(community);
        insertApplication(later, insertUser("申込み済み"), "applied", 30);
        insertApplication(later, insertUser("1番目"), "waitlisted", 20);
        insertApplication(later, me, "waitlisted", 10);
        insertApplication(sooner, me, "pending", 5);
        insertApplication(ended, me, "applied", 100);

        List<MyEventRow> rows = eventMapper.findMyEvents(me);

        assertThat(rows).extracting(MyEventRow::id).containsExactly(sooner, later);
        assertThat(rows).extracting(MyEventRow::status).containsExactly("pending", "waitlisted");
        assertThat(rows.get(0).waitlistPosition()).isNull();
        assertThat(rows.get(1).waitlistPosition()).isEqualTo(2);
        assertThat(rows.get(1).remainingSeats()).isZero();
    }

    // ---------- 作成・編集・削除 ----------

    /**
     * 【何のため】作成と編集で、すべての項目が保存されることを確かめる（承認の方法も含む）。
     */
    @Test
    void 作成と編集ですべての項目を保存する() {
        long community = insertCommunity(insertUser("オーナー"));
        Instant now = eventMapper.now().truncatedTo(ChronoUnit.SECONDS);
        ValidEventInput input = new ValidEventInput("タイトル", "説明", now.plus(2, ChronoUnit.DAYS),
                now.plus(3, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), "場所", 20, "manual", "auto");

        long id = eventMapper.insert(community, input);
        EventRow created = eventMapper.find(community, id);

        assertThat(created.title()).isEqualTo("タイトル");
        assertThat(created.startAt()).isEqualTo(input.startAt());
        assertThat(created.applicationDeadline()).isEqualTo(input.applicationDeadline());
        assertThat(created.capacity()).isEqualTo(20);
        assertThat(created.memberApproval()).isEqualTo("manual");
        assertThat(created.nonMemberApproval()).isEqualTo("auto");

        eventMapper.update(id, new ValidEventInput("新しいタイトル", "", input.startAt(), input.endAt(),
                input.applicationDeadline(), "新しい場所", 30, "auto", "closed"));
        EventRow updated = eventMapper.find(community, id);
        assertThat(updated.title()).isEqualTo("新しいタイトル");
        assertThat(updated.location()).isEqualTo("新しい場所");
        assertThat(updated.capacity()).isEqualTo(30);
        assertThat(updated.nonMemberApproval()).isEqualTo("closed");
    }

    /**
     * 【何のため】イベントを削除すると、申込みも一緒に消え（ON DELETE CASCADE）、お知らせは残って、
     * イベントの番号だけが空になる（ON DELETE SET NULL）ことを確かめる。
     */
    @Test
    void 削除すると申込みは消えてお知らせは残る() {
        long owner = insertUser("オーナー");
        long community = insertCommunity(owner);
        long event = insertUpcomingEvent(community, 10);
        insertApplication(event, insertUser("申込み"), "applied", 5);
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, event_title, community_id, event_id)
                VALUES (?, 'application_approved', 't', ?, ?)
                """, owner, community, event);

        eventMapper.delete(event);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM applications WHERE event_id = ?", Long.class,
                event)).isZero();
        assertThat(jdbcTemplate.queryForList(
                "SELECT event_id FROM notifications WHERE user_id = ? AND community_id = ?", Long.class, owner,
                community)).containsExactly((Long) null);
    }
}

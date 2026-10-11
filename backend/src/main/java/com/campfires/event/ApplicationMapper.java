package com.campfires.event;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 【役割】申込み（applications テーブル）の SQL の窓口。
 * 【なぜ必要か】申込み、取り消し、承認・却下、キャンセル待ちの繰り上げ、申込者の一覧を、SQL で行うため。
 * SQL の本文は src/main/resources/mapper/event/ApplicationMapper.xml。
 *
 * 申込みの状態：pending（承認待ち）、applied（申込み済み）、waitlisted（キャンセル待ち）、rejected（却下）。
 * 席の順番（キャンセル待ちの順番、繰り上げの順番）は queued_at（席の順番を取った日時）で決める。
 */
@Mapper
public interface ApplicationMapper {

    /**
     * 【何のため】ユーザーが申し込んでいる、開催前（終了していない）のイベントの番号を、番号の小さい順に返す。
     * @param communityId 指定したときは、そのコミュニティのイベントだけ。null なら、すべてのコミュニティ
     */
    List<Long> findUpcomingEventIdsOfUser(@Param("userId") long userId, @Param("communityId") Long communityId);

    /**
     * 【何のため】イベントの行をロックする（SELECT … FOR UPDATE）。
     * 同じイベントへの申込み・取り消し・繰り上げが同時に起きても、人数がずれないように、順番に処理させる。
     */
    Long lockEvent(@Param("eventId") long eventId);

    /**
     * 【何のため】イベントとユーザーから、取り消す申込みを探す。なければ null。却下された申込みは、取り消せないので含めない。
     */
    CancelTarget findCancelTarget(@Param("eventId") long eventId, @Param("userId") long userId);

    /**
     * 【何のため】申込みを削除する。
     */
    void delete(@Param("applicationId") long applicationId);

    /**
     * 【何のため】自分の申込みの状況（キャンセル待ちなら何番目か）を返す。申し込んでいなければ null。
     */
    MyApplication findMyApplication(@Param("eventId") long eventId, @Param("userId") long userId);

    /**
     * 【何のため】申込みを登録する。
     * 同じイベントにすでに申し込んでいれば、データベースの制約で DuplicateKeyException が投げられる。
     * @param status pending（承認待ち）、applied（申込み済み）、waitlisted（キャンセル待ち）のどれか
     */
    void insert(@Param("eventId") long eventId, @Param("userId") long userId, @Param("status") String status);

    /**
     * 【何のため】申込者（承認待ち、キャンセル待ちを含む。却下は除く）の数を数える。
     */
    long countApplications(@Param("eventId") long eventId);

    /**
     * 【何のため】申込者の1ページ分を返す。
     * 承認待ちの人を申し込んだ順、申込み済みの人を席を取った順、キャンセル待ちの人を順番どおり、の順に並べる。
     */
    List<ApplicationRow> findApplications(@Param("eventId") long eventId, @Param("limit") int limit,
            @Param("offset") long offset);

    /**
     * 【何のため】1人の申込みを返す（申込者の一覧と同じ形）。なければ null。却下された申込みも返す。
     */
    ApplicationRow findApplication(@Param("eventId") long eventId, @Param("userId") long userId);

    /**
     * 【何のため】承認待ちの申込みを、申し込んだ順に返す（手動から自動に変えたとき、まとめて承認するため）。
     * @param member true ならコミュニティのメンバーの申込み、false ならメンバー以外の申込み
     */
    List<PendingApplication> findPending(@Param("eventId") long eventId, @Param("member") boolean member);

    /**
     * 【何のため】承認待ちの申込みを、承認する（申込み済みか、キャンセル待ちにする）。席の順番は、今の日時で取る。
     * @return 書き換えた行の数（承認待ちでなければ 0）
     */
    int approve(@Param("eventId") long eventId, @Param("userId") long userId, @Param("status") String status);

    /**
     * 【何のため】承認待ちの申込みを、却下する。
     * @return 書き換えた行の数（承認待ちでなければ 0）
     */
    int reject(@Param("eventId") long eventId, @Param("userId") long userId);

    /**
     * 【何のため】空いている枠の数だけ、キャンセル待ちの先頭から申込み済みに繰り上げる。締め切り後は繰り上げない。
     */
    void promoteWaitlist(@Param("eventId") long eventId);
}

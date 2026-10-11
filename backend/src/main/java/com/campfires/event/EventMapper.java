package com.campfires.event;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 【役割】イベント（events テーブル）の SQL の窓口。
 * 【なぜ必要か】イベントの一覧・検索・作成・編集・削除を、SQL で行うため。
 * SQL の本文は src/main/resources/mapper/event/EventMapper.xml。
 */
@Mapper
public interface EventMapper {

    /**
     * 【何のため】一覧・検索に当てはまる、開催前のイベントの数を数える。
     * @param words 検索の単語。空なら、すべて
     */
    long countSearch(@Param("words") List<String> words);

    /**
     * 【何のため】一覧・検索の1ページ分を返す。並び順は要件定義書 5.8 のとおり。
     */
    List<EventSummary> search(@Param("words") List<String> words, @Param("limit") int limit,
            @Param("offset") long offset);

    /**
     * 【何のため】自分が申し込んだ、開催前のイベント（キャンセル待ちを含む）を、開始日時の近い順に全件返す。
     */
    List<MyEventRow> findMyEvents(@Param("userId") long userId);

    /**
     * 【何のため】コミュニティのイベントの数を数える。
     * @param past true なら終了済み、false なら開催前
     */
    long countCommunityEvents(@Param("communityId") long communityId, @Param("past") boolean past);

    /**
     * 【何のため】コミュニティのイベントの1ページ分を返す。開催前は開始日時の近い順、終了済みは開始日時の新しい順。
     */
    List<EventSummary> findCommunityEvents(@Param("communityId") long communityId, @Param("past") boolean past,
            @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 【何のため】自分がオーナーか運営スタッフをしているコミュニティのイベントの数を数える。
     * @param past true なら終了済み、false なら開催前
     */
    long countManaged(@Param("userId") long userId, @Param("past") boolean past);

    /**
     * 【何のため】自分がオーナーか運営スタッフをしているコミュニティのイベントの1ページ分を返す。
     * 開催前は開始日時の近い順、終了済みは開始日時の新しい順。
     */
    List<ManagedEvent> findManaged(@Param("userId") long userId, @Param("past") boolean past,
            @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 【何のため】コミュニティとイベントの番号で、イベントを探す。
     * イベントが、そのコミュニティのものでなければ null（見つからないものとして扱う）。
     */
    EventRow find(@Param("communityId") long communityId, @Param("eventId") long eventId);

    /**
     * 【何のため】イベントの行をロックする（SELECT … FOR UPDATE）。イベントがなければ null。
     * 同じイベントへの申込み・取り消し・編集が同時に起きても、定員を超えないように、順番に処理させる（F-14）。
     * 人数などは、ロックを取った後に find で読み直す（理由は EventMapper.xml の lock を参照）。
     */
    Long lock(@Param("communityId") long communityId, @Param("eventId") long eventId);

    /**
     * 【何のため】イベントを作り、番号を返す。
     */
    long insert(@Param("communityId") long communityId, @Param("input") ValidEventInput input);

    /**
     * 【何のため】イベントの内容を変える。
     */
    void update(@Param("eventId") long eventId, @Param("input") ValidEventInput input);

    /**
     * 【何のため】イベントを削除する。申込みも一緒に削除される（テーブルの ON DELETE CASCADE）。
     */
    void delete(@Param("eventId") long eventId);

    /**
     * 【何のため】データベースの今の日時を返す（日時のチェックを、データベースと同じ時計で行うため）。
     */
    Instant now();
}

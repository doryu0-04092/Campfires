package com.campfires.notification;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 【役割】お知らせ（notifications テーブル）の SQL の窓口。
 * 【なぜ必要か】ほかの機能（申込みの取り消し、イベントの削除）からお知らせを作り、
 * お知らせの API で、一覧を返したり、既読にしたりするため。
 * SQL の本文は src/main/resources/mapper/notification/NotificationMapper.xml。
 * どのメソッドも userId（ログインしている人）で絞り、他人あてのお知らせには触れない。
 */
@Mapper
public interface NotificationMapper {

    /**
     * 【何のため】締め切り後のキャンセルを、そのコミュニティのオーナーと運営スタッフに知らせる（要件定義書 5.10）。
     * @param eventId        キャンセルされたイベント
     * @param actorUserId    キャンセルした人
     * @param operatorUserId 操作をした人。この人にはお知らせを送らない
     */
    void insertLateCancellation(@Param("eventId") long eventId, @Param("actorUserId") long actorUserId,
            @Param("operatorUserId") long operatorUserId);

    /**
     * 【何のため】イベントの削除を、申し込んでいた人（キャンセル待ちを含む）に知らせる（要件定義書 5.10）。
     * イベントを削除する前に呼ぶ（削除した後は、申込みも消えていて、誰に送るか分からないため）。
     * @param eventId        削除するイベント
     * @param operatorUserId 削除の操作をした人。この人にはお知らせを送らない
     */
    void insertEventDeleted(@Param("eventId") long eventId, @Param("operatorUserId") long operatorUserId);

    /**
     * 【何のため】参加の承認・却下を、申し込んだ人に知らせる（要件定義書 5.10、5.12）。
     * @param type application_approved（承認された）か application_rejected（却下された）
     */
    void insertApplicationDecided(@Param("eventId") long eventId, @Param("userId") long userId,
            @Param("type") String type);

    /**
     * 【何のため】自分あてのお知らせの数を数える。
     */
    long countByUser(@Param("userId") long userId);

    /**
     * 【何のため】自分あてのお知らせの1ページ分を、新しい順に返す。
     */
    List<NotificationRow> findByUser(@Param("userId") long userId, @Param("limit") int limit,
            @Param("offset") long offset);

    /**
     * 【何のため】自分あての、未読のお知らせの数を数える。
     */
    long countUnread(@Param("userId") long userId);

    /**
     * 【何のため】自分あてのお知らせを1件探す。ない、または他人あてなら null。
     */
    NotificationRow findOne(@Param("userId") long userId, @Param("id") long id);

    /**
     * 【何のため】自分あてのお知らせを1件、既読にする。すでに既読なら、既読にした日時は変えない。
     */
    void markRead(@Param("userId") long userId, @Param("id") long id);

    /**
     * 【何のため】自分あての未読のお知らせを、すべて既読にする。
     */
    void markAllRead(@Param("userId") long userId);
}

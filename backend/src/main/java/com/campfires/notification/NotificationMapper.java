package com.campfires.notification;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 【役割】お知らせ（notifications テーブル）の SQL の窓口。
 * 【なぜ必要か】ほかの機能（申込みの取り消し、イベントの削除）から、お知らせを作るため。
 * SQL の本文は src/main/resources/mapper/notification/NotificationMapper.xml。
 * お知らせの API を作るときに、ここへメソッドを足していく。
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
}

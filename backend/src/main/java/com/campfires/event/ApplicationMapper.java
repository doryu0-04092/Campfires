package com.campfires.event;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 【役割】申込み（applications テーブル）の SQL の窓口。
 * 【なぜ必要か】申込みの取り消しと、キャンセル待ちの繰り上げを、SQL で行うため。
 * SQL の本文は src/main/resources/mapper/event/ApplicationMapper.xml。
 * 申込みの API（イベントの機能）を作るときに、ここへメソッドを足していく。
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
     * 【何のため】イベントとユーザーから、取り消す申込みを探す。なければ null。
     */
    CancelTarget findCancelTarget(@Param("eventId") long eventId, @Param("userId") long userId);

    /**
     * 【何のため】申込みを削除する。
     */
    void delete(@Param("applicationId") long applicationId);

    /**
     * 【何のため】空いている枠の数だけ、キャンセル待ちの先頭から申込み済みに繰り上げる。締め切り後は繰り上げない。
     */
    void promoteWaitlist(@Param("eventId") long eventId);
}

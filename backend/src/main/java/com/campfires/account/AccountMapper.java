package com.campfires.account;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 【役割】アカウントの削除で使う、コミュニティとメンバーの SQL の窓口。
 * 【なぜ必要か】アカウントの削除には「オーナーをしているコミュニティが残っていないか」などの確認が必要。
 * その確認のための SQL を、アカウントの機能の中にまとめておく。
 * SQL の本文は src/main/resources/mapper/account/AccountMapper.xml。
 */
@Mapper
public interface AccountMapper {

    /**
     * 【何のため】オーナーをしていて、アーカイブしていないコミュニティの数を数える。
     */
    int countActiveOwnedCommunities(@Param("userId") long userId);

    /**
     * 【何のため】オーナーをしているコミュニティ（アーカイブ済みを含む）に、開催前（終了していない）のイベントがあるかどうか。
     */
    boolean existsUpcomingEventInOwnedCommunities(@Param("userId") long userId);

    /**
     * 【何のため】参加しているすべてのコミュニティから退会する。
     * オーナーの行は残す（アーカイブ済みのコミュニティのオーナーとして「退会したユーザー」と表示するため）。
     */
    void deleteMembershipsExceptOwner(@Param("userId") long userId);
}

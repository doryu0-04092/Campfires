package com.campfires.community;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 【役割】メンバー（memberships テーブル）と参加申請（join_requests テーブル）の SQL の窓口。
 * 【なぜ必要か】参加の申請、承認・却下、運営スタッフの任命・解除、除名、退会を、SQL で行うため。
 * SQL の本文は src/main/resources/mapper/community/MemberMapper.xml。
 */
@Mapper
public interface MemberMapper {

    /**
     * 【何のため】コミュニティでの、ユーザーの立場（owner、staff、member）を返す。メンバーでなければ null。
     */
    String findRole(@Param("communityId") long communityId, @Param("userId") long userId);

    /**
     * 【何のため】ユーザーの、いちばん新しい参加申請の状態を返す。申請したことがなければ null。
     */
    String findLatestJoinRequestStatus(@Param("communityId") long communityId, @Param("userId") long userId);

    /**
     * 【何のため】参加を申請する（状態は「申請中」から始まる）。登録した申請を返す。
     * すでに申請中なら、データベースの制約で DuplicateKeyException が投げられる。
     */
    JoinRequestRow insertJoinRequest(@Param("communityId") long communityId, @Param("userId") long userId);

    /**
     * 【何のため】申請中の参加申請の数を数える。
     */
    long countPendingJoinRequests(@Param("communityId") long communityId);

    /**
     * 【何のため】申請中の参加申請の1ページ分を、申請した日時の古い順に返す。
     */
    List<JoinRequestRow> findPendingJoinRequests(@Param("communityId") long communityId, @Param("limit") int limit,
            @Param("offset") long offset);

    /**
     * 【何のため】コミュニティの参加申請を、番号で探す。見つからなければ null。
     */
    JoinRequestRow findJoinRequest(@Param("communityId") long communityId, @Param("id") long id);

    /**
     * 【何のため】申請中の参加申請を、承認または却下にする。
     * @return 書き換えた行の数（すでに処理済みなら 0）
     */
    int decideJoinRequest(@Param("communityId") long communityId, @Param("id") long id,
            @Param("status") String status);

    /**
     * 【何のため】申請中の参加申請を、すべて却下にする（アーカイブしたとき。要件定義書 5.9）。
     */
    void rejectPendingJoinRequests(@Param("communityId") long communityId);

    /**
     * 【何のため】メンバーにする。すでにメンバーなら、何もしない。
     */
    void insertMembership(@Param("communityId") long communityId, @Param("userId") long userId,
            @Param("role") String role);

    /**
     * 【何のため】メンバーの数を数える。
     * @param group managers ならオーナーと運営スタッフだけ、members なら一般のメンバーだけ、null なら全員
     */
    long countMembers(@Param("communityId") long communityId, @Param("group") String group);

    /**
     * 【何のため】メンバーの1ページ分を、立場の順（オーナー → 運営スタッフ → メンバー）、
     * 同じ立場なら参加した日時の古い順に返す。
     * @param group managers ならオーナーと運営スタッフだけ、members なら一般のメンバーだけ、null なら全員
     */
    List<MemberRow> findMembers(@Param("communityId") long communityId, @Param("group") String group,
            @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 【何のため】メンバーを1人探す。メンバーでなければ null。
     */
    MemberRow findMember(@Param("communityId") long communityId, @Param("userId") long userId);

    /**
     * 【何のため】メンバーの立場を変える。
     */
    void updateRole(@Param("communityId") long communityId, @Param("userId") long userId, @Param("role") String role);

    /**
     * 【何のため】メンバーから外す（除名、退会）。
     */
    void deleteMembership(@Param("communityId") long communityId, @Param("userId") long userId);
}

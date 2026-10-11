package com.campfires.community;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 【役割】コミュニティ（communities テーブル）の SQL の窓口。
 * 【なぜ必要か】コミュニティの一覧・検索・作成・編集・アーカイブを、SQL で行うため。
 * SQL の本文は src/main/resources/mapper/community/CommunityMapper.xml。
 */
@Mapper
public interface CommunityMapper {

    /**
     * 【何のため】一覧・検索に当てはまるコミュニティの数を数える（アーカイブ済みは含めない）。
     * @param words 検索の単語。空なら、すべて
     */
    long countSearch(@Param("words") List<String> words);

    /**
     * 【何のため】一覧・検索の1ページ分を返す。並び順は要件定義書 5.11 のとおり。
     */
    List<CommunitySummary> search(@Param("words") List<String> words, @Param("limit") int limit,
            @Param("offset") long offset);

    /**
     * 【何のため】自分が「メンバー」の立場で参加している、アーカイブしていないコミュニティを、全件返す。
     */
    List<CommunitySummary> findMemberCommunities(@Param("userId") long userId);

    /**
     * 【何のため】自分がオーナーか運営スタッフをしているコミュニティの数を数える（アーカイブ済みを含む）。
     */
    long countManaged(@Param("userId") long userId);

    /**
     * 【何のため】自分がオーナーか運営スタッフをしているコミュニティの1ページ分を、作成日時の新しい順に返す。
     */
    List<ManagedCommunity> findManaged(@Param("userId") long userId, @Param("limit") int limit,
            @Param("offset") long offset);

    /**
     * 【何のため】番号でコミュニティを探す。見つからなければ null。
     */
    Community findById(@Param("id") long id);

    /**
     * 【何のため】番号でコミュニティを探し、同時に行をロックする（SELECT … FOR UPDATE）。
     * 参加の申請とアーカイブが同時に起きても、アーカイブ中のコミュニティに申請が残らないように、順番に処理させる。
     */
    Community findByIdForUpdate(@Param("id") long id);

    /**
     * 【何のため】コミュニティを作り、番号を返す。
     */
    long insert(@Param("name") String name, @Param("description") String description,
            @Param("ownerId") long ownerId);

    /**
     * 【何のため】名前と説明を変える。
     */
    void update(@Param("id") long id, @Param("name") String name, @Param("description") String description);

    /**
     * 【何のため】アーカイブする（すでにアーカイブ中なら、何もしない）。
     */
    void archive(@Param("id") long id);

    /**
     * 【何のため】アーカイブを元に戻す。
     */
    void unarchive(@Param("id") long id);
}

package com.campfires.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.campfires.MapperTestBase;

/**
 * 【役割】コミュニティ・メンバー・参加申請の SQL（CommunityMapper.xml、MemberMapper.xml）を、本物の PostgreSQL で直接確かめるテスト。
 * 【なぜ必要か】コミュニティの検索の並び順（要件定義書 5.11）、メンバーの一覧の並び順と絞り込み、
 * 参加申請の「申請中は1件だけ」の制約、アーカイブを2回しても日時が変わらないことなど、SQL に書いたルールを確かめるため。
 */
class CommunityMapperSqlTest extends MapperTestBase {

    @Autowired
    private CommunityMapper communityMapper;
    @Autowired
    private MemberMapper memberMapper;

    // ---------- コミュニティの検索 ----------

    /**
     * 【何のため】検索は、当てはまった単語の数が多い順、同じなら作成日時の新しい順で、アーカイブ中は出さないことを確かめる。
     * メンバーの人数も数える。
     */
    @Test
    void 検索は単語の数が多い順でアーカイブ中は出さない() {
        String word = uniqueWord();
        long owner = insertUser("オーナー");
        long one = insertCommunity(owner, word, false, 1);
        long two = insertCommunity(owner, word + " Spring", false, 5);
        long older = insertCommunity(owner, "古い " + word, false, 3);
        insertCommunity(owner, word + " spring", true, 0);
        insertMember(two, insertUser("メンバー"), "member");

        List<CommunitySummary> found = communityMapper.search(List.of(word, "spring"), 300, 0).stream()
                .filter(c -> List.of(one, two, older).contains(c.id()))
                .toList();

        assertThat(found).extracting(CommunitySummary::id).containsExactly(two, one, older);
        assertThat(found.get(0).memberCount()).isEqualTo(2);
        assertThat(communityMapper.countSearch(List.of(word))).isEqualTo(3);
    }

    /**
     * 【何のため】説明も検索の対象で、大文字・小文字を区別しないことを確かめる。
     */
    @Test
    void 説明も大文字小文字を区別せずに探す() {
        String word = uniqueWord();
        long owner = insertUser("オーナー");
        long id = communityMapper.insert("名前", "説明に " + word.toUpperCase() + " を含む", owner);

        assertThat(communityMapper.search(List.of(word), 30, 0)).extracting(CommunitySummary::id).containsExactly(id);
    }

    // ---------- 自分のコミュニティ ----------

    /**
     * 【何のため】「メンバーとして参加しているコミュニティ」は、立場がメンバーのものだけ（オーナー・運営スタッフは除く）、
     * アーカイブ中も除き、新しい順に返すことを確かめる。
     */
    @Test
    void メンバーとして参加しているコミュニティだけを返す() {
        long me = insertUser("わたし");
        long other = insertUser("ほかの人");
        long older = insertCommunity(other, "古い", false, 10);
        long newer = insertCommunity(other, "新しい", false, 1);
        long archived = insertCommunity(other, "アーカイブ中", true, 5);
        long staffed = insertCommunity(other, "運営スタッフ", false, 2);
        insertCommunity(me, "自分がオーナー", false, 0);
        insertMember(older, me, "member");
        insertMember(newer, me, "member");
        insertMember(archived, me, "member");
        insertMember(staffed, me, "staff");

        assertThat(communityMapper.findMemberCommunities(me)).extracting(CommunitySummary::id)
                .containsExactly(newer, older);
    }

    /**
     * 【何のため】管理しているコミュニティは、オーナーと運営スタッフのもの（アーカイブ中を含む）で、
     * 自分の立場と申請中の参加申請の数が入ることを確かめる。
     */
    @Test
    void 管理しているコミュニティに申請中の数が入る() {
        long me = insertUser("わたし");
        long other = insertUser("ほかの人");
        long owned = insertCommunity(me, "オーナー", false, 1);
        long staffed = insertCommunity(other, "運営スタッフ", true, 2);
        insertMember(staffed, me, "staff");
        long joined = insertCommunity(other, "メンバー", false, 0);
        insertMember(joined, me, "member");
        memberMapper.insertJoinRequest(owned, insertUser("申請1"));
        memberMapper.insertJoinRequest(owned, insertUser("申請2"));
        long decided = memberMapper.insertJoinRequest(owned, insertUser("却下")).id();
        memberMapper.decideJoinRequest(owned, decided, "rejected");

        assertThat(communityMapper.countManaged(me)).isEqualTo(2);
        List<ManagedCommunity> managed = communityMapper.findManaged(me, 30, 0);
        assertThat(managed).extracting(ManagedCommunity::id).containsExactly(owned, staffed);
        assertThat(managed).extracting(ManagedCommunity::myRole).containsExactly("owner", "staff");
        assertThat(managed).extracting(ManagedCommunity::pendingJoinRequestCount).containsExactly(2L, 0L);
    }

    // ---------- アーカイブ ----------

    /**
     * 【何のため】すでにアーカイブ中なら、もう一度アーカイブしても、アーカイブした日時を変えないことを確かめる。
     * 元に戻すと、日時が空になる。
     */
    @Test
    void アーカイブを2回してもアーカイブした日時は変わらない() {
        long owner = insertUser("オーナー");
        long id = insertCommunity(owner);
        jdbcTemplate.update("UPDATE communities SET archived_at = now() - interval '3 days' WHERE id = ?", id);
        Instant first = communityMapper.findById(id).archivedAt();

        communityMapper.archive(id);
        assertThat(communityMapper.findById(id).archivedAt()).isEqualTo(first);

        communityMapper.unarchive(id);
        assertThat(communityMapper.findById(id).archivedAt()).isNull();
        communityMapper.archive(id);
        assertThat(communityMapper.findById(id).isArchived()).isTrue();
    }

    /**
     * 【何のため】詳細には、オーナーのニックネームとメンバーの人数が入り、ないコミュニティは null になることを確かめる。
     */
    @Test
    void 詳細にオーナーのニックネームとメンバーの人数が入る() {
        long owner = insertUser("オーナーさん");
        long id = insertCommunity(owner);
        insertMember(id, insertUser("メンバー"), "member");

        Community community = communityMapper.findByIdForUpdate(id);

        assertThat(community.ownerNickname()).isEqualTo("オーナーさん");
        assertThat(community.memberCount()).isEqualTo(2);
        assertThat(communityMapper.findById(-1)).isNull();
    }

    // ---------- メンバー ----------

    /**
     * 【何のため】メンバーの一覧は、オーナー → 運営スタッフ → メンバーの順、同じ立場なら参加した日時の古い順で、
     * group で絞り込めることを確かめる。
     */
    @Test
    void メンバーの一覧は立場の順で絞り込める() {
        long owner = insertUser("オーナー");
        long community = insertCommunity(owner);
        long staffNew = insertUser("新しい運営スタッフ");
        long staffOld = insertUser("古い運営スタッフ");
        long member = insertUser("メンバー");
        insertMember(community, member, "member", 100);
        insertMember(community, staffNew, "staff", 10);
        insertMember(community, staffOld, "staff", 50);

        assertThat(memberMapper.findMembers(community, null, 30, 0)).extracting(MemberRow::userId)
                .containsExactly(owner, staffOld, staffNew, member);
        assertThat(memberMapper.countMembers(community, null)).isEqualTo(4);
        assertThat(memberMapper.countMembers(community, "managers")).isEqualTo(3);
        assertThat(memberMapper.findMembers(community, "members", 30, 0)).extracting(MemberRow::userId)
                .containsExactly(member);
        assertThat(memberMapper.findMembers(community, "managers", 1, 1)).extracting(MemberRow::userId)
                .containsExactly(staffOld);
    }

    /**
     * 【何のため】すでにメンバーの人をもう一度入れても、何もしない（立場も変えない）ことを確かめる。
     */
    @Test
    void すでにメンバーなら入れ直しても立場は変わらない() {
        long owner = insertUser("オーナー");
        long community = insertCommunity(owner);
        long staff = insertUser("運営スタッフ");
        insertMember(community, staff, "staff");

        memberMapper.insertMembership(community, staff, "member");

        assertThat(memberMapper.findRole(community, staff)).isEqualTo("staff");
    }

    // ---------- 参加申請 ----------

    /**
     * 【何のため】いちばん新しい参加申請の状態を返し、申請中の申請だけを古い順に一覧にすることを確かめる。
     * 却下された後に、もう一度申請できる（申請中が1件だけなら制約に引っかからない）。
     */
    @Test
    void いちばん新しい参加申請の状態を返す() {
        long owner = insertUser("オーナー");
        long community = insertCommunity(owner);
        long user = insertUser("申請する人");
        assertThat(memberMapper.findLatestJoinRequestStatus(community, user)).isNull();

        JoinRequestRow first = memberMapper.insertJoinRequest(community, user);
        assertThat(first.nickname()).isEqualTo("申請する人");
        assertThat(first.status()).isEqualTo("pending");
        assertThat(memberMapper.decideJoinRequest(community, first.id(), "rejected")).isEqualTo(1);
        assertThat(memberMapper.decideJoinRequest(community, first.id(), "approved")).isZero();
        JoinRequestRow second = memberMapper.insertJoinRequest(community, user);

        assertThat(memberMapper.findLatestJoinRequestStatus(community, user)).isEqualTo("pending");
        assertThat(memberMapper.countPendingJoinRequests(community)).isEqualTo(1);
        assertThat(memberMapper.findPendingJoinRequests(community, 30, 0)).extracting(JoinRequestRow::id)
                .containsExactly(second.id());
    }

    /**
     * 【何のため】ほかのコミュニティの番号では、参加申請を見つけられず、処理もできないことを確かめる。
     */
    @Test
    void ほかのコミュニティの参加申請は処理できない() {
        long owner = insertUser("オーナー");
        long mine = insertCommunity(owner);
        long other = insertCommunity(owner);
        long request = memberMapper.insertJoinRequest(other, insertUser("申請")).id();

        assertThat(memberMapper.findJoinRequest(mine, request)).isNull();
        assertThat(memberMapper.decideJoinRequest(mine, request, "approved")).isZero();
    }

    /**
     * 【何のため】アーカイブのときに申請中の参加申請を却下すると、そのコミュニティの申請中だけが却下されることを確かめる。
     */
    @Test
    void アーカイブで却下するのはそのコミュニティの申請中だけ() {
        long owner = insertUser("オーナー");
        long target = insertCommunity(owner);
        long other = insertCommunity(owner);
        long user = insertUser("申請");
        long inTarget = memberMapper.insertJoinRequest(target, user).id();
        long inOther = memberMapper.insertJoinRequest(other, user).id();

        memberMapper.rejectPendingJoinRequests(target);

        assertThat(memberMapper.findJoinRequest(target, inTarget).status()).isEqualTo("rejected");
        assertThat(memberMapper.findJoinRequest(other, inOther).status()).isEqualTo("pending");
    }

    /**
     * 【何のため】同じコミュニティに、申請中の参加申請は1件だけ（データベースの制約で止まる）であることを確かめる。
     * 制約で止まると、このトランザクションの中では、それ以降の SQL を実行できないので、最後に確かめる。
     */
    @Test
    void 申請中の参加申請は1件だけ() {
        long community = insertCommunity(insertUser("オーナー"));
        long user = insertUser("申請");
        memberMapper.insertJoinRequest(community, user);

        assertThatThrownBy(() -> memberMapper.insertJoinRequest(community, user))
                .isInstanceOf(DuplicateKeyException.class);
    }
}

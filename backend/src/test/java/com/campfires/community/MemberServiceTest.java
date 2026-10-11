package com.campfires.community;

import static com.campfires.ApiExceptionAssertions.assertApiError;
import static com.campfires.community.CommunityGuardTest.COMMUNITY_ID;
import static com.campfires.community.CommunityGuardTest.OWNER;
import static com.campfires.community.CommunityGuardTest.community;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import com.campfires.event.ApplicationCancelService;

/**
 * 【役割】参加申請とメンバーの管理の業務の処理（MemberService）の単体テスト。
 * 【なぜ必要か】立場の変更・除名・退会・参加申請のルール（要件定義書 5.1、5.4、5.4.1）と、
 * 権限のチェック（オーナーだけ、オーナーと運営スタッフだけ）の組み合わせを、データベースなしで確かめるため。
 * 権限のチェックは本物の CommunityGuard を使い、その先の Mapper だけを偽物にする。
 */
@ExtendWith(MockitoExtension.class)
// LENIENT（ゆるい）：教えていない値での呼び出し（メンバーでない人の立場を調べる、など）も、null を返すだけにする。
// ふだんの設定（STRICT_STUBS）では、教えた値と違う値で呼ぶと、まちがいとして止まってしまうため
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberServiceTest {

    // 運営スタッフ、メンバー、メンバー以外のユーザーの番号
    private static final long STAFF = 200;
    private static final long MEMBER = 300;
    private static final long OUTSIDER = 400;

    @Mock
    private CommunityMapper communityMapper;
    @Mock
    private MemberMapper memberMapper;
    @Mock
    private ApplicationCancelService cancelService;

    private MemberService service;

    @BeforeEach
    void setUp() {
        service = new MemberService(memberMapper, new CommunityGuard(communityMapper, memberMapper), cancelService);
    }

    /** 【何のため】メンバーの行を作る。 */
    private static MemberRow member(long userId, String role) {
        return new MemberRow(userId, "ニックネーム", role, Instant.EPOCH);
    }

    // ---------- 立場の変更 ----------

    /**
     * 【何のため】立場を変えられるのはオーナーだけ（運営スタッフは 403）であることを確かめる。
     */
    @Test
    void 立場を変えられるのはオーナーだけ() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(false));

        assertApiError(() -> service.updateRole(STAFF, COMMUNITY_ID, MEMBER, "staff"), 403, "FORBIDDEN");
        verify(memberMapper, never()).updateRole(anyLong(), anyLong(), anyString());
    }

    /**
     * 【何のため】メンバーでない人の立場は変えられず（404）、オーナーの立場も変えられない（CANNOT_CHANGE_OWNER）ことを確かめる。
     */
    @Test
    void メンバーでない人とオーナーの立場は変えられない() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(false));
        when(memberMapper.findMember(COMMUNITY_ID, OWNER)).thenReturn(member(OWNER, "owner"));

        assertApiError(() -> service.updateRole(OWNER, COMMUNITY_ID, OUTSIDER, "staff"), 404, "NOT_FOUND");
        assertApiError(() -> service.updateRole(OWNER, COMMUNITY_ID, OWNER, "member"), 409, "CANNOT_CHANGE_OWNER");
        verify(memberMapper, never()).updateRole(anyLong(), anyLong(), anyString());
    }

    /**
     * 【何のため】オーナーは、メンバーを運営スタッフにでき、変えた後のメンバーを返すことを確かめる。
     */
    @Test
    void オーナーはメンバーを運営スタッフにできる() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(false));
        when(memberMapper.findMember(COMMUNITY_ID, MEMBER))
                .thenReturn(member(MEMBER, "member"))
                .thenReturn(member(MEMBER, "staff"));

        MemberResponse response = service.updateRole(OWNER, COMMUNITY_ID, MEMBER, "staff");

        verify(memberMapper).updateRole(COMMUNITY_ID, MEMBER, "staff");
        assertThat(response.role()).isEqualTo("staff");
    }

    // ---------- 除名 ----------

    /**
     * 【何のため】オーナーは除名できない（CANNOT_REMOVE_OWNER）ことを確かめる。
     */
    @Test
    void オーナーは除名できない() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(false));
        when(memberMapper.findMember(COMMUNITY_ID, OWNER)).thenReturn(member(OWNER, "owner"));

        assertApiError(() -> service.removeMember(OWNER, COMMUNITY_ID, OWNER), 409, "CANNOT_REMOVE_OWNER");
        verifyNoInteractions(cancelService);
    }

    /**
     * 【何のため】除名すると、メンバーから外した後に、このコミュニティの申込みを取り消すことを確かめる。
     * 取り消しの操作をしたのはオーナー（締め切り後なら、オーナー以外の運営スタッフにお知らせが届く）。
     */
    @Test
    void 除名するとこのコミュニティの申込みを取り消す() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(false));
        when(memberMapper.findMember(COMMUNITY_ID, STAFF)).thenReturn(member(STAFF, "staff"));

        service.removeMember(OWNER, COMMUNITY_ID, STAFF);

        InOrder order = inOrder(memberMapper, cancelService);
        order.verify(memberMapper).deleteMembership(COMMUNITY_ID, STAFF);
        order.verify(cancelService).cancelUpcomingApplications(STAFF, OWNER, COMMUNITY_ID);
    }

    // ---------- 退会 ----------

    /**
     * 【何のため】メンバーでなければ NOT_MEMBER、オーナーなら OWNER_CANNOT_LEAVE で、退会できないことを確かめる。
     */
    @Test
    void メンバーでない人とオーナーは退会できない() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(false));
        when(memberMapper.findRole(COMMUNITY_ID, OWNER)).thenReturn("owner");

        assertApiError(() -> service.leave(OUTSIDER, COMMUNITY_ID), 409, "NOT_MEMBER");
        assertApiError(() -> service.leave(OWNER, COMMUNITY_ID), 409, "OWNER_CANNOT_LEAVE");
        verify(memberMapper, never()).deleteMembership(anyLong(), anyLong());
    }

    /**
     * 【何のため】運営スタッフは退会でき、このコミュニティの申込みを、本人の操作として取り消すことを確かめる。
     * アーカイブ中のコミュニティからも退会できる。
     */
    @Test
    void 運営スタッフはアーカイブ中でも退会できる() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(true));
        when(memberMapper.findRole(COMMUNITY_ID, STAFF)).thenReturn("staff");

        service.leave(STAFF, COMMUNITY_ID);

        verify(memberMapper).deleteMembership(COMMUNITY_ID, STAFF);
        verify(cancelService).cancelUpcomingApplications(STAFF, STAFF, COMMUNITY_ID);
    }

    // ---------- 参加申請 ----------

    /**
     * 【何のため】アーカイブ中のコミュニティには申請できず（COMMUNITY_ARCHIVED）、メンバーは申請できない（ALREADY_MEMBER）ことを確かめる。
     */
    @Test
    void アーカイブ中とメンバーは申請できない() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(true)).thenReturn(community(false));
        when(memberMapper.findRole(COMMUNITY_ID, MEMBER)).thenReturn("member");

        assertApiError(() -> service.createJoinRequest(MEMBER, COMMUNITY_ID), 409, "COMMUNITY_ARCHIVED");
        assertApiError(() -> service.createJoinRequest(MEMBER, COMMUNITY_ID), 409, "ALREADY_MEMBER");
        verify(memberMapper, never()).insertJoinRequest(anyLong(), anyLong());
    }

    /**
     * 【何のため】すでに申請中なら（データベースの制約で止まる）、JOIN_REQUEST_PENDING になることを確かめる。
     */
    @Test
    void 申請中ならもう一度申請できない() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(false));
        when(memberMapper.insertJoinRequest(COMMUNITY_ID, OUTSIDER))
                .thenThrow(new DuplicateKeyException("join_requests_one_pending"));

        assertApiError(() -> service.createJoinRequest(OUTSIDER, COMMUNITY_ID), 409, "JOIN_REQUEST_PENDING");
    }

    /**
     * 【何のため】参加申請がなければ 404、すでに処理されていれば JOIN_REQUEST_ALREADY_DECIDED で、メンバーにしないことを確かめる。
     */
    @Test
    void 処理できない参加申請はメンバーにしない() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(false));
        when(memberMapper.findRole(COMMUNITY_ID, STAFF)).thenReturn("staff");

        assertApiError(() -> service.decideJoinRequest(STAFF, COMMUNITY_ID, 9, "approved"), 404, "NOT_FOUND");

        when(memberMapper.findJoinRequest(COMMUNITY_ID, 9))
                .thenReturn(new JoinRequestRow(9, OUTSIDER, "n", "approved", Instant.EPOCH));
        assertApiError(() -> service.decideJoinRequest(STAFF, COMMUNITY_ID, 9, "approved"), 409,
                "JOIN_REQUEST_ALREADY_DECIDED");
        verify(memberMapper, never()).insertMembership(anyLong(), anyLong(), anyString());
    }

    /**
     * 【何のため】承認したら申請した人を「メンバー」として入れ、却下したら入れないことを確かめる。
     */
    @Test
    void 承認したらメンバーにして却下ならしない() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(false));
        when(memberMapper.findRole(COMMUNITY_ID, STAFF)).thenReturn("staff");
        when(memberMapper.findJoinRequest(COMMUNITY_ID, 9))
                .thenReturn(new JoinRequestRow(9, OUTSIDER, "n", "pending", Instant.EPOCH));
        when(memberMapper.findJoinRequest(COMMUNITY_ID, 10))
                .thenReturn(new JoinRequestRow(10, MEMBER, "n", "pending", Instant.EPOCH));
        when(memberMapper.decideJoinRequest(COMMUNITY_ID, 9, "approved")).thenReturn(1);
        when(memberMapper.decideJoinRequest(COMMUNITY_ID, 10, "rejected")).thenReturn(1);

        service.decideJoinRequest(STAFF, COMMUNITY_ID, 9, "approved");
        service.decideJoinRequest(STAFF, COMMUNITY_ID, 10, "rejected");

        verify(memberMapper).insertMembership(COMMUNITY_ID, OUTSIDER, "member");
        verify(memberMapper, never()).insertMembership(COMMUNITY_ID, MEMBER, "member");
    }

    /**
     * 【何のため】メンバーの一覧の group が、決まった値（managers、members）以外なら、全員（null）として扱うことを確かめる。
     */
    @Test
    void 決まっていないgroupは全員として扱う() {
        when(communityMapper.findById(COMMUNITY_ID)).thenReturn(community(false));
        when(memberMapper.findRole(COMMUNITY_ID, OWNER)).thenReturn("owner");

        service.listMembers(OWNER, COMMUNITY_ID, null, "owner' OR '1'='1");
        service.listMembers(OWNER, COMMUNITY_ID, null, "managers");

        verify(memberMapper).countMembers(eq(COMMUNITY_ID), isNull());
        verify(memberMapper).countMembers(COMMUNITY_ID, "managers");
    }
}

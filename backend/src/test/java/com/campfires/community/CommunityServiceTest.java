package com.campfires.community;

import static com.campfires.ApiExceptionAssertions.assertApiError;
import static com.campfires.community.CommunityGuardTest.COMMUNITY_ID;
import static com.campfires.community.CommunityGuardTest.OWNER;
import static com.campfires.community.CommunityGuardTest.community;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 【役割】コミュニティの一覧・検索・作成・編集・アーカイブの業務の処理（CommunityService）の単体テスト。
 * 【なぜ必要か】詳細に出す「自分の参加申請の状態」の決め方（承認済みなら出さない）や、
 * アーカイブを2回しても参加申請の却下を繰り返さないことなど、API のテストでは確かめにくい分かれ道を確かめるため。
 */
@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    private static final long VIEWER = 500;

    @Mock
    private CommunityMapper communityMapper;
    @Mock
    private MemberMapper memberMapper;

    private CommunityService service;

    @BeforeEach
    void setUp() {
        service = new CommunityService(communityMapper, memberMapper, new CommunityGuard(communityMapper, memberMapper));
    }

    /**
     * 【何のため】メンバーでない人には、いちばん新しい参加申請の状態を返し、承認済み（退会・除名した後）なら返さないことを確かめる。
     */
    @Test
    void 承認済みの参加申請の状態は返さない() {
        when(communityMapper.findById(COMMUNITY_ID)).thenReturn(community(false));
        when(memberMapper.findLatestJoinRequestStatus(COMMUNITY_ID, VIEWER))
                .thenReturn("pending").thenReturn("rejected").thenReturn("approved");

        assertThat(service.get(COMMUNITY_ID, VIEWER).myJoinRequestStatus()).isEqualTo("pending");
        assertThat(service.get(COMMUNITY_ID, VIEWER).myJoinRequestStatus()).isEqualTo("rejected");
        assertThat(service.get(COMMUNITY_ID, VIEWER).myJoinRequestStatus()).isNull();
    }

    /**
     * 【何のため】メンバーには立場を返し、参加申請は調べないこと、ログインしていなければどちらも調べないことを確かめる。
     */
    @Test
    void メンバーとログインしていない人は参加申請を調べない() {
        when(communityMapper.findById(COMMUNITY_ID)).thenReturn(community(false));
        when(memberMapper.findRole(COMMUNITY_ID, VIEWER)).thenReturn("member");

        assertThat(service.get(COMMUNITY_ID, VIEWER).myRole()).isEqualTo("member");
        assertThat(service.get(COMMUNITY_ID, null).myRole()).isNull();
        verify(memberMapper, never()).findLatestJoinRequestStatus(anyLong(), anyLong());
    }

    /**
     * 【何のため】アーカイブ中のコミュニティは編集できない（COMMUNITY_ARCHIVED）、オーナー以外は 403 になることを確かめる。
     */
    @Test
    void アーカイブ中とオーナー以外は編集できない() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(true));

        assertApiError(() -> service.update(OWNER, COMMUNITY_ID, new CommunityInput("名前", "")), 409,
                "COMMUNITY_ARCHIVED");
        assertApiError(() -> service.update(VIEWER, COMMUNITY_ID, new CommunityInput("名前", "")), 403, "FORBIDDEN");
        verify(communityMapper, never()).update(anyLong(), anyString(), anyString());
    }

    /**
     * 【何のため】アーカイブすると、申請中の参加申請を却下することを確かめる。
     */
    @Test
    void アーカイブすると参加申請を却下する() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(false));
        when(communityMapper.findById(COMMUNITY_ID)).thenReturn(community(true));

        service.archive(OWNER, COMMUNITY_ID);

        verify(communityMapper).archive(COMMUNITY_ID);
        verify(memberMapper).rejectPendingJoinRequests(COMMUNITY_ID);
    }

    /**
     * 【何のため】すでにアーカイブ中なら、もう一度アーカイブしても何もしない（アーカイブした日時も変えない）ことを確かめる。
     */
    @Test
    void アーカイブ中にアーカイブしても何もしない() {
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(true));
        when(communityMapper.findById(COMMUNITY_ID)).thenReturn(community(true));

        service.archive(OWNER, COMMUNITY_ID);

        verify(communityMapper, never()).archive(anyLong());
        verify(memberMapper, never()).rejectPendingJoinRequests(anyLong());
    }

    /**
     * 【何のため】作ったコミュニティに、作った人をオーナーとして入れることを確かめる。
     */
    @Test
    void 作った人がオーナーになる() {
        when(communityMapper.insert("名前", "", OWNER)).thenReturn(COMMUNITY_ID);
        when(communityMapper.findById(COMMUNITY_ID)).thenReturn(community(false));

        service.create(OWNER, new CommunityInput(" 名前 ", null));

        verify(memberMapper).insertMembership(COMMUNITY_ID, OWNER, "owner");
    }

    /**
     * 【何のため】検索で300件目より後ろのページは、SQL でコミュニティを取り出さないことを確かめる。
     */
    @Test
    void 検索の上限より後ろのページは取り出さない() {
        when(communityMapper.countSearch(List.of("java"))).thenReturn(301L);

        var response = service.search("java", "11");

        verify(communityMapper, never()).search(any(), anyInt(), anyLong());
        assertThat(response.totalCount()).isEqualTo(300);
        assertThat(response.limitExceeded()).isTrue();
    }
}

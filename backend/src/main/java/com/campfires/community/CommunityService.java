package com.campfires.community;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campfires.common.search.SearchQuery;
import com.campfires.common.web.ApiException;
import com.campfires.common.web.PageResponse;
import com.campfires.common.web.Paging;
import com.campfires.common.web.SearchPageResponse;

/**
 * 【役割】コミュニティの一覧・検索・作成・編集・アーカイブの、業務の処理をするクラス。
 * 【なぜ必要か】要件定義書 5.9（アーカイブ）、5.11（一覧と検索）のルールと、権限のチェックを、1か所にまとめるため。
 */
@Service
public class CommunityService {

    private final CommunityMapper communityMapper;
    private final MemberMapper memberMapper;
    private final CommunityGuard guard;

    public CommunityService(CommunityMapper communityMapper, MemberMapper memberMapper, CommunityGuard guard) {
        this.communityMapper = communityMapper;
        this.memberMapper = memberMapper;
        this.guard = guard;
    }

    /**
     * 【何のため】コミュニティの一覧・検索（要件定義書 5.11）。アーカイブ済みは含めない。
     * 検索したときは、当てはまった単語の数が多い順、300件まで。
     */
    public SearchPageResponse<CommunitySummary> search(String q, String page) {
        SearchQuery query = SearchQuery.of(q, page);
        long matched = communityMapper.countSearch(query.words());
        List<CommunitySummary> items = query.limit() > 0
                ? communityMapper.search(query.words(), query.limit(), query.offset())
                : List.of();
        return SearchPageResponse.of(items, query.totalCount(matched), query.page(), query.limitExceeded(matched));
    }

    /**
     * 【何のため】自分が「メンバー」の立場で参加しているコミュニティを、全件返す（要件定義書 5.11）。
     */
    public MyCommunityListResponse findMemberCommunities(long userId) {
        return new MyCommunityListResponse(communityMapper.findMemberCommunities(userId));
    }

    /**
     * 【何のため】管理しているコミュニティ（オーナーか運営スタッフ）の一覧（S-16）。アーカイブ済みも含める。
     */
    public PageResponse<ManagedCommunity> findManaged(long userId, String pageValue) {
        int page = Paging.parsePage(pageValue);
        long total = communityMapper.countManaged(userId);
        return PageResponse.of(communityMapper.findManaged(userId, Paging.PAGE_SIZE, Paging.offset(page)), total, page);
    }

    /**
     * 【何のため】コミュニティを作る。作った人がオーナーになる。
     */
    @Transactional
    public CommunityDetail create(long userId, CommunityInput input) {
        long id = communityMapper.insert(input.name(), input.description(), userId);
        // 作った人を、オーナーとしてメンバーに入れる
        memberMapper.insertMembership(id, userId, "owner");
        return detail(guard.requireCommunity(id), userId);
    }

    /**
     * 【何のため】コミュニティの詳細を返す。ログインしていれば、自分との関係も入れる。
     * @param viewerId 見ている人の番号。ログインしていなければ null
     */
    public CommunityDetail get(long communityId, Long viewerId) {
        return detail(guard.requireCommunity(communityId), viewerId);
    }

    /**
     * 【何のため】名前と説明を変える。オーナーだけ。アーカイブ中はできない（要件定義書 5.9）。
     */
    @Transactional
    public CommunityDetail update(long userId, long communityId, CommunityInput input) {
        Community community = guard.requireCommunityForUpdate(communityId);
        guard.requireOwner(community, userId);
        if (community.isArchived()) {
            throw ApiException.conflict("COMMUNITY_ARCHIVED",
                    "アーカイブ中のコミュニティは編集できません。先に元に戻してください。");
        }
        communityMapper.update(communityId, input.name(), input.description());
        return detail(guard.requireCommunity(communityId), userId);
    }

    /**
     * 【何のため】アーカイブする。オーナーだけ。申請中の参加申請は、すべて却下にする（要件定義書 5.9）。
     * すでにアーカイブ中なら、何も変えずに成功を返す。
     */
    @Transactional
    public CommunityDetail archive(long userId, long communityId) {
        Community community = guard.requireCommunityForUpdate(communityId);
        guard.requireOwner(community, userId);
        if (!community.isArchived()) {
            communityMapper.archive(communityId);
            memberMapper.rejectPendingJoinRequests(communityId);
        }
        return detail(guard.requireCommunity(communityId), userId);
    }

    /**
     * 【何のため】アーカイブを元に戻す。オーナーだけ。却下した参加申請は、元に戻らない。
     */
    @Transactional
    public CommunityDetail unarchive(long userId, long communityId) {
        Community community = guard.requireCommunityForUpdate(communityId);
        guard.requireOwner(community, userId);
        communityMapper.unarchive(communityId);
        return detail(guard.requireCommunity(communityId), userId);
    }

    /**
     * 【何のため】コミュニティの詳細の返事を作る。見ている人との関係（立場、参加申請の状態）も入れる。
     */
    private CommunityDetail detail(Community community, Long viewerId) {
        String myRole = null;
        String myJoinRequestStatus = null;
        if (viewerId != null) {
            myRole = guard.roleOf(community.id(), viewerId);
            // メンバーでなければ、自分のいちばん新しい参加申請の状態を返す
            if (myRole == null) {
                String latest = memberMapper.findLatestJoinRequestStatus(community.id(), viewerId);
                // 承認済みなのにメンバーでない（退会した・除名された）ときは、申請していない扱いにする
                myJoinRequestStatus = "approved".equals(latest) ? null : latest;
            }
        }
        return CommunityDetail.of(community, myRole, myJoinRequestStatus);
    }
}

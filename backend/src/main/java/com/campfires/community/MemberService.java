package com.campfires.community;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campfires.common.web.ApiException;
import com.campfires.common.web.PageResponse;
import com.campfires.common.web.Paging;
import com.campfires.event.ApplicationCancelService;

/**
 * 【役割】参加申請とメンバーの管理の、業務の処理をするクラス。
 * 【なぜ必要か】参加申請（要件定義書 5.1）、退会（5.4）、除名（5.4.1）のルールと、権限のチェックを、1か所にまとめるため。
 */
@Service
public class MemberService {

    private final MemberMapper memberMapper;
    private final CommunityGuard guard;
    private final ApplicationCancelService applicationCancelService;

    public MemberService(MemberMapper memberMapper, CommunityGuard guard,
            ApplicationCancelService applicationCancelService) {
        this.memberMapper = memberMapper;
        this.guard = guard;
        this.applicationCancelService = applicationCancelService;
    }

    // ---------- 参加申請 ----------

    /**
     * 【何のため】申請中の参加申請の一覧を、申請した日時の古い順に返す。オーナーと運営スタッフだけ。
     */
    public PageResponse<JoinRequestResponse> listJoinRequests(long userId, long communityId, String pageValue) {
        Community community = guard.requireCommunity(communityId);
        guard.requireManager(community, userId);
        int page = Paging.parsePage(pageValue);
        long total = memberMapper.countPendingJoinRequests(communityId);
        var items = memberMapper.findPendingJoinRequests(communityId, Paging.PAGE_SIZE, Paging.offset(page)).stream()
                .map(JoinRequestRow::toResponse)
                .toList();
        return PageResponse.of(items, total, page);
    }

    /**
     * 【何のため】コミュニティへの参加を申請する。却下された人、退会した人、除名された人も、もう一度申請できる。
     */
    @Transactional
    public JoinRequestResponse createJoinRequest(long userId, long communityId) {
        // コミュニティの行をロックして、アーカイブと同時に申請されても、アーカイブ中のコミュニティに申請が残らないようにする
        Community community = guard.requireCommunityForUpdate(communityId);
        if (community.isArchived()) {
            throw ApiException.conflict("COMMUNITY_ARCHIVED", "アーカイブ中のコミュニティには、参加を申請できません。");
        }
        if (guard.roleOf(communityId, userId) != null) {
            throw ApiException.conflict("ALREADY_MEMBER", "すでにこのコミュニティのメンバーです。");
        }
        try {
            return memberMapper.insertJoinRequest(communityId, userId).toResponse();
        } catch (DuplicateKeyException e) {
            // 申請中の申請は1件だけ（データベースの制約 join_requests_one_pending で止まる）
            throw ApiException.conflict("JOIN_REQUEST_PENDING", "すでに参加を申請しています。承認されるまでお待ちください。");
        }
    }

    /**
     * 【何のため】参加申請を承認・却下する。オーナーと運営スタッフだけ。承認したら、申請した人をメンバーにする。
     * @param status approved（承認）か rejected（却下）
     */
    @Transactional
    public JoinRequestResponse decideJoinRequest(long userId, long communityId, long joinRequestId, String status) {
        Community community = guard.requireCommunityForUpdate(communityId);
        guard.requireManager(community, userId);
        JoinRequestRow joinRequest = memberMapper.findJoinRequest(communityId, joinRequestId);
        if (joinRequest == null) {
            throw ApiException.notFound("参加申請が見つかりません。");
        }
        // 申請中のときだけ書き換える。すでに処理されていれば（ほかの運営スタッフが先に処理した、アーカイブで却下された）409
        if (memberMapper.decideJoinRequest(communityId, joinRequestId, status) == 0) {
            throw ApiException.conflict("JOIN_REQUEST_ALREADY_DECIDED", "この参加申請は、すでに承認または却下されています。");
        }
        if ("approved".equals(status)) {
            memberMapper.insertMembership(communityId, joinRequest.userId(), "member");
        }
        return memberMapper.findJoinRequest(communityId, joinRequestId).toResponse();
    }

    // ---------- メンバー ----------

    /**
     * 【何のため】メンバーの一覧を返す。オーナーと運営スタッフだけ。
     * @param groupValue managers ならオーナーと運営スタッフだけ、members なら一般のメンバーだけ。それ以外（なしを含む）は全員
     */
    public PageResponse<MemberResponse> listMembers(long userId, long communityId, String pageValue,
            String groupValue) {
        Community community = guard.requireCommunity(communityId);
        guard.requireManager(community, userId);
        int page = Paging.parsePage(pageValue);
        // 決まった値だけを使う（それ以外の値は、全員として扱う）
        String group = "managers".equals(groupValue) || "members".equals(groupValue) ? groupValue : null;
        long total = memberMapper.countMembers(communityId, group);
        var items = memberMapper.findMembers(communityId, group, Paging.PAGE_SIZE, Paging.offset(page)).stream()
                .map(MemberRow::toResponse)
                .toList();
        return PageResponse.of(items, total, page);
    }

    /**
     * 【何のため】運営スタッフに任命する・メンバーに戻す。オーナーだけ。オーナーの立場は変えられない。
     * @param role staff か member
     */
    @Transactional
    public MemberResponse updateRole(long userId, long communityId, long targetUserId, String role) {
        Community community = guard.requireCommunityForUpdate(communityId);
        guard.requireOwner(community, userId);
        MemberRow member = requireMember(communityId, targetUserId);
        if ("owner".equals(member.role())) {
            throw ApiException.conflict("CANNOT_CHANGE_OWNER", "オーナーの立場は変えられません。");
        }
        memberMapper.updateRole(communityId, targetUserId, role);
        return memberMapper.findMember(communityId, targetUserId).toResponse();
    }

    /**
     * 【何のため】メンバーを除名する。オーナーだけ。
     * 除名された人の、このコミュニティの開催前のイベントへの申込みは、すべて取り消す（要件定義書 5.4.1）。
     */
    @Transactional
    public void removeMember(long userId, long communityId, long targetUserId) {
        Community community = guard.requireCommunityForUpdate(communityId);
        guard.requireOwner(community, userId);
        MemberRow member = requireMember(communityId, targetUserId);
        if ("owner".equals(member.role())) {
            throw ApiException.conflict("CANNOT_REMOVE_OWNER", "オーナーは除名できません。");
        }
        memberMapper.deleteMembership(communityId, targetUserId);
        // 取り消しの操作をしたのはオーナー（締め切り後なら、オーナー以外の運営スタッフにお知らせが届く）
        applicationCancelService.cancelUpcomingApplications(targetUserId, userId, communityId);
    }

    /**
     * 【何のため】コミュニティを退会する。メンバーと運営スタッフ（オーナーは退会できない）。
     * このコミュニティの開催前のイベントへの申込みは、すべて取り消す（要件定義書 5.4）。
     */
    @Transactional
    public void leave(long userId, long communityId) {
        guard.requireCommunityForUpdate(communityId);
        String role = guard.roleOf(communityId, userId);
        if (role == null) {
            throw ApiException.conflict("NOT_MEMBER", "このコミュニティのメンバーではありません。");
        }
        if ("owner".equals(role)) {
            throw ApiException.conflict("OWNER_CANNOT_LEAVE", "オーナーは退会できません。コミュニティをアーカイブしてください。");
        }
        memberMapper.deleteMembership(communityId, userId);
        applicationCancelService.cancelUpcomingApplications(userId, userId, communityId);
    }

    // メンバーを探す。メンバーでなければ 404
    private MemberRow requireMember(long communityId, long userId) {
        MemberRow member = memberMapper.findMember(communityId, userId);
        if (member == null) {
            throw ApiException.notFound("メンバーが見つかりません。");
        }
        return member;
    }
}

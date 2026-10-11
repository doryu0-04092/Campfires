package com.campfires.community;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.campfires.common.security.LoginUser;
import com.campfires.common.web.PageResponse;

import jakarta.validation.Valid;

/**
 * 【役割】参加申請とメンバーの API（docs/api/openapi.yaml の members）。
 * 【なぜ必要か】画面から届いたデータを受け取り、MemberService に処理を頼み、結果を返事にするため。
 * どの API もログインが必要（SecurityConfig で、ログインしていなければ 401 にしている）。
 */
@RestController
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    /**
     * 【何のため】申請中の参加申請の一覧（GET /api/communities/{communityId}/join-requests）。
     */
    @GetMapping("/api/communities/{communityId}/join-requests")
    public PageResponse<JoinRequestResponse> listJoinRequests(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable long communityId, @RequestParam(required = false) String page) {
        return memberService.listJoinRequests(loginUser.id(), communityId, page);
    }

    /**
     * 【何のため】参加を申請する（POST /api/communities/{communityId}/join-requests）。201 を返す。
     */
    @PostMapping("/api/communities/{communityId}/join-requests")
    public ResponseEntity<JoinRequestResponse> createJoinRequest(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable long communityId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.createJoinRequest(loginUser.id(), communityId));
    }

    /**
     * 【何のため】参加申請を承認・却下する（PATCH /api/communities/{communityId}/join-requests/{joinRequestId}）。
     */
    @PatchMapping("/api/communities/{communityId}/join-requests/{joinRequestId}")
    public JoinRequestResponse decideJoinRequest(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable long communityId, @PathVariable long joinRequestId,
            @Valid @RequestBody DecideJoinRequestRequest body) {
        return memberService.decideJoinRequest(loginUser.id(), communityId, joinRequestId, body.status());
    }

    /**
     * 【何のため】メンバーの一覧（GET /api/communities/{communityId}/members）。
     * group=managers ならオーナーと運営スタッフだけ、group=members なら一般のメンバーだけを返す。
     */
    @GetMapping("/api/communities/{communityId}/members")
    public PageResponse<MemberResponse> listMembers(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable long communityId, @RequestParam(required = false) String page,
            @RequestParam(required = false) String group) {
        return memberService.listMembers(loginUser.id(), communityId, page, group);
    }

    /**
     * 【何のため】運営スタッフに任命する・メンバーに戻す（PATCH /api/communities/{communityId}/members/{userId}）。
     */
    @PatchMapping("/api/communities/{communityId}/members/{userId}")
    public MemberResponse updateRole(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long communityId,
            @PathVariable long userId, @Valid @RequestBody UpdateMemberRoleRequest body) {
        return memberService.updateRole(loginUser.id(), communityId, userId, body.role());
    }

    /**
     * 【何のため】メンバーを除名する（DELETE /api/communities/{communityId}/members/{userId}）。204 を返す。
     */
    @DeleteMapping("/api/communities/{communityId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable long communityId, @PathVariable long userId) {
        memberService.removeMember(loginUser.id(), communityId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 【何のため】コミュニティを退会する（DELETE /api/communities/{communityId}/membership）。204 を返す。
     */
    @DeleteMapping("/api/communities/{communityId}/membership")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long communityId) {
        memberService.leave(loginUser.id(), communityId);
        return ResponseEntity.noContent().build();
    }
}

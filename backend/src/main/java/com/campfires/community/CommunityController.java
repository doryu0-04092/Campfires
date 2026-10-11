package com.campfires.community;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.campfires.common.security.LoginUser;
import com.campfires.common.web.PageResponse;
import com.campfires.common.web.SearchPageResponse;

import jakarta.validation.Valid;

/**
 * 【役割】コミュニティの API（docs/api/openapi.yaml の communities と、account の
 * /api/me/communities、/api/me/managed-communities）。
 * 【なぜ必要か】画面から届いたデータを受け取り、CommunityService に処理を頼み、結果を返事にするため。
 *
 * @PathVariable は「URL の {名前} の部分を、この引数に入れる」、
 * @RequestParam は「URL の ?名前=値 の値を、この引数に入れる」という印（required = false で、なくてもよい）。
 * ページ番号は、数でない値でもエラーにしないように、文字で受け取る。
 */
@RestController
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    /**
     * 【何のため】コミュニティの一覧・検索（GET /api/communities）。ログインしていなくても呼べる。
     */
    @GetMapping("/api/communities")
    public SearchPageResponse<CommunitySummary> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String page) {
        return communityService.search(q, page);
    }

    /**
     * 【何のため】自分がメンバーとして参加しているコミュニティの一覧（GET /api/me/communities）。
     */
    @GetMapping("/api/me/communities")
    public MyCommunityListResponse myCommunities(@AuthenticationPrincipal LoginUser loginUser) {
        return communityService.findMemberCommunities(loginUser.id());
    }

    /**
     * 【何のため】管理しているコミュニティの一覧（GET /api/me/managed-communities）。
     */
    @GetMapping("/api/me/managed-communities")
    public PageResponse<ManagedCommunity> managedCommunities(@AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) String page) {
        return communityService.findManaged(loginUser.id(), page);
    }

    /**
     * 【何のため】コミュニティを作る（POST /api/communities）。201 と、作ったコミュニティの URL（Location）を返す。
     */
    @PostMapping("/api/communities")
    public ResponseEntity<CommunityDetail> create(@AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody CommunityInput body) {
        CommunityDetail detail = communityService.create(loginUser.id(), body);
        return ResponseEntity.created(URI.create("/api/communities/" + detail.id())).body(detail);
    }

    /**
     * 【何のため】コミュニティの詳細（GET /api/communities/{communityId}）。ログインしていなくても呼べる。
     * ログインしていないときは、loginUser が null になる。
     */
    @GetMapping("/api/communities/{communityId}")
    public CommunityDetail get(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long communityId) {
        return communityService.get(communityId, loginUser == null ? null : loginUser.id());
    }

    /**
     * 【何のため】コミュニティを編集する（PATCH /api/communities/{communityId}）。
     */
    @PatchMapping("/api/communities/{communityId}")
    public CommunityDetail update(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long communityId,
            @Valid @RequestBody CommunityInput body) {
        return communityService.update(loginUser.id(), communityId, body);
    }

    /**
     * 【何のため】アーカイブする（PUT /api/communities/{communityId}/archive）。
     */
    @PutMapping("/api/communities/{communityId}/archive")
    public CommunityDetail archive(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long communityId) {
        return communityService.archive(loginUser.id(), communityId);
    }

    /**
     * 【何のため】アーカイブを元に戻す（DELETE /api/communities/{communityId}/archive）。
     */
    @DeleteMapping("/api/communities/{communityId}/archive")
    public CommunityDetail unarchive(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long communityId) {
        return communityService.unarchive(loginUser.id(), communityId);
    }
}

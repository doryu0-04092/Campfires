package com.campfires.community;

import org.springframework.stereotype.Component;

import com.campfires.common.web.ApiException;

/**
 * 【役割】「コミュニティがあるか」「その人に権限があるか」を確かめる処理をまとめたクラス。
 * 【なぜ必要か】権限のチェック（要件定義書 3章の表）は、コミュニティ、メンバー、イベントの多くの API で行う。
 * 同じチェックを1か所にまとめ、書き忘れや食い違いを防ぐ。
 * 権限がなければ 403、コミュニティがなければ 404 のエラーを投げる。
 */
@Component
public class CommunityGuard {

    private final CommunityMapper communityMapper;
    private final MemberMapper memberMapper;

    public CommunityGuard(CommunityMapper communityMapper, MemberMapper memberMapper) {
        this.communityMapper = communityMapper;
        this.memberMapper = memberMapper;
    }

    /**
     * 【何のため】コミュニティを探す。見つからなければ 404。
     */
    public Community requireCommunity(long communityId) {
        Community community = communityMapper.findById(communityId);
        if (community == null) {
            throw notFound();
        }
        return community;
    }

    /**
     * 【何のため】コミュニティを探し、行をロックする。見つからなければ 404。
     * 同じコミュニティへの操作（参加の申請、承認、アーカイブなど）を、順番に処理させたいときに使う。
     */
    public Community requireCommunityForUpdate(long communityId) {
        Community community = communityMapper.findByIdForUpdate(communityId);
        if (community == null) {
            throw notFound();
        }
        return community;
    }

    /**
     * 【何のため】コミュニティでの、ユーザーの立場を返す。メンバーでなければ null。
     */
    public String roleOf(long communityId, long userId) {
        return memberMapper.findRole(communityId, userId);
    }

    /**
     * 【何のため】オーナーでなければ 403 にする。
     */
    public void requireOwner(Community community, long userId) {
        if (!community.isOwner(userId)) {
            throw ApiException.forbidden();
        }
    }

    /**
     * 【何のため】オーナーか運営スタッフでなければ 403 にする。立場を返す。
     */
    public String requireManager(Community community, long userId) {
        String role = roleOf(community.id(), userId);
        if (!"owner".equals(role) && !"staff".equals(role)) {
            throw ApiException.forbidden();
        }
        return role;
    }

    private static ApiException notFound() {
        return ApiException.notFound("コミュニティが見つかりません。");
    }
}

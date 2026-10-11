package com.campfires.community;

import java.util.List;

/**
 * 【役割】自分がメンバーとして参加しているコミュニティの一覧の返事（docs/api/openapi.yaml の MyCommunityListResponse）。
 * 【なぜ必要か】参加しているコミュニティは数が限られるので、ページに分けずに全件を items に入れて返す（要件定義書 5.11）。
 *
 * @param items 参加しているコミュニティ（作成日時の新しい順）
 */
public record MyCommunityListResponse(List<CommunitySummary> items) {
}

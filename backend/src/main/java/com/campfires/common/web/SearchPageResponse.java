package com.campfires.common.web;

import java.util.List;

/**
 * 【役割】検索の結果の返事。ページの情報に、limitExceeded（300件の上限を超えたか）を足した形。
 * 【なぜ必要か】イベントとコミュニティの一覧・検索の返事（EventListResponse、CommunityListResponse）を、同じ形にするため。
 *
 * @param items         そのページのデータ
 * @param totalCount    全部の件数（検索したときは、300件まで）
 * @param page          今のページ番号
 * @param pageSize      1ページの件数（30）
 * @param totalPages    全部で何ページあるか
 * @param limitExceeded 検索の結果が300件を超えて、切り捨てたかどうか（画面は「条件を絞ってください」と表示する）
 */
public record SearchPageResponse<T>(List<T> items, long totalCount, int page, int pageSize, int totalPages,
        boolean limitExceeded) {

    /**
     * 【何のため】そのページのデータと全部の件数から、返事を作る。
     */
    public static <T> SearchPageResponse<T> of(List<T> items, long totalCount, int page, boolean limitExceeded) {
        return new SearchPageResponse<>(items, totalCount, page, Paging.PAGE_SIZE, Paging.totalPages(totalCount),
                limitExceeded);
    }
}

package com.campfires.notification;

import java.util.List;

import com.campfires.common.web.Paging;

/**
 * 【役割】お知らせの一覧の返事（docs/api/openapi.yaml の NotificationListResponse）。
 * 【なぜ必要か】ページの情報に、未読の件数（全部の中で）を足して返すため。
 *
 * @param items       そのページのお知らせ（新しい順）
 * @param totalCount  全部の件数
 * @param page        今のページ番号
 * @param pageSize    1ページの件数（30）
 * @param totalPages  全部で何ページあるか
 * @param unreadCount 未読のお知らせの件数
 */
public record NotificationListResponse(
        List<NotificationResponse> items,
        long totalCount,
        int page,
        int pageSize,
        int totalPages,
        long unreadCount) {

    /**
     * 【何のため】そのページのお知らせと件数から、返事を作る。
     */
    public static NotificationListResponse of(List<NotificationResponse> items, long totalCount, int page,
            long unreadCount) {
        return new NotificationListResponse(items, totalCount, page, Paging.PAGE_SIZE, Paging.totalPages(totalCount),
                unreadCount);
    }
}

package com.campfires.common.web;

import java.util.List;

/**
 * 【役割】ページに分けた一覧の返事（docs/api/openapi.yaml の PageInfo と items）。
 * 【なぜ必要か】一覧を返す API の返事を、すべて同じ形にするため。
 * <T> は「一覧の中身の形は、使う側が決める」という印（例：PageResponse<Member>）。
 *
 * @param items      そのページのデータ
 * @param totalCount 全部の件数
 * @param page       今のページ番号
 * @param pageSize   1ページの件数（30）
 * @param totalPages 全部で何ページあるか
 */
public record PageResponse<T>(List<T> items, long totalCount, int page, int pageSize, int totalPages) {

    /**
     * 【何のため】そのページのデータと全部の件数から、返事を作る。
     */
    public static <T> PageResponse<T> of(List<T> items, long totalCount, int page) {
        return new PageResponse<>(items, totalCount, page, Paging.PAGE_SIZE, Paging.totalPages(totalCount));
    }
}

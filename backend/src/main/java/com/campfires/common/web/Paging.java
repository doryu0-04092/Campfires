package com.campfires.common.web;

/**
 * 【役割】一覧のページ分けの計算をまとめたクラス（docs/api/README.md 1.8）。
 * 【なぜ必要か】一覧を返す API は、すべて「1ページ30件、?page=2 で2ページ目」という同じルール。
 * ページ番号の読み取りと、何件目から取り出すかの計算を、1か所にまとめる。
 */
public final class Paging {

    // 1ページの件数
    public static final int PAGE_SIZE = 30;

    private Paging() {
    }

    /**
     * 【何のため】URL の page の値を、ページ番号に変える。
     * 数でない、または 1 より小さいときは 1 にする（偽の API と同じ扱い）。
     * 引数を文字で受け取るのは、数でない値（?page=abc）でも、エラーにせずに1ページ目を返すため。
     */
    public static int parsePage(String value) {
        if (value == null) {
            return 1;
        }
        try {
            int page = Integer.parseInt(value.strip());
            return Math.max(page, 1);
        } catch (NumberFormatException e) {
            // 数に変えられない値（abc、とても大きな数など）
            return 1;
        }
    }

    /**
     * 【何のため】そのページの、最初のデータが何件目か（0から数える）を計算する。
     * long（大きな数を入れられる型）にして、とても大きなページ番号でも計算があふれないようにする。
     */
    public static long offset(int page) {
        return (long) (page - 1) * PAGE_SIZE;
    }

    /**
     * 【何のため】全部で何ページあるかを計算する。データが0件でも、1ページとする。
     */
    public static int totalPages(long totalCount) {
        // (件数 + 29) ÷ 30 の切り捨ては、件数 ÷ 30 の切り上げと同じ
        return (int) Math.max(1, (totalCount + PAGE_SIZE - 1) / PAGE_SIZE);
    }
}

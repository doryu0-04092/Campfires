package com.campfires.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 【役割】ページ分けの計算（Paging、PageResponse、SearchPageResponse）の単体テスト。
 * 【なぜ必要か】すべての一覧の API が、このクラスの計算を使う。
 * 「?page=abc」「?page=0」のような変な値や、とても大きなページ番号でも、エラーにならず正しく計算できることを、
 * データベースを使わずに、すばやく確かめるため。
 *
 * Spring もデータベースも起動しない（ただの Java のテスト）ので、一瞬で終わる。
 */
class PagingTest {

    /**
     * 【何のため】page の値が送られてこない・空・空白だけのときは、1ページ目になることを確かめる。
     * @ParameterizedTest は「同じテストを、いろいろな値で何回も動かす」という印。
     * @NullAndEmptySource は null と ""（空の文字）を、@ValueSource は書いた値を、1つずつ渡してくれる。
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "abc", "1.5", "0", "-1", "-2147483648", "99999999999", "1e3" })
    void 数でない値や1より小さい値は1ページ目になる(String value) {
        assertThat(Paging.parsePage(value)).isEqualTo(1);
    }

    /**
     * 【何のため】正しい数なら、そのページ番号になることを確かめる（前後の空白と「+」の付いた数も読める）。
     * @CsvSource は「入力, 期待する答え」の組を、1行ずつ渡してくれる。
     */
    @ParameterizedTest
    @CsvSource({ "1, 1", "2, 2", "' 3 ', 3", "+4, 4", "2147483647, 2147483647" })
    void 正しい数はそのページ番号になる(String value, int expected) {
        assertThat(Paging.parsePage(value)).isEqualTo(expected);
    }

    /**
     * 【何のため】何件目から取り出すか（offset）の計算を確かめる。1ページ目は0件目から、2ページ目は30件目から。
     */
    @Test
    void offsetは1ページ30件で計算する() {
        assertThat(Paging.offset(1)).isZero();
        assertThat(Paging.offset(2)).isEqualTo(30);
        assertThat(Paging.offset(11)).isEqualTo(300);
    }

    /**
     * 【何のため】とても大きなページ番号でも、計算があふれて（マイナスになって）しまわないことを確かめる。
     * int のままかけ算すると、約21億を超えたところでマイナスになってしまう。
     */
    @Test
    void とても大きなページ番号でもoffsetがあふれない() {
        long expected = (long) (Integer.MAX_VALUE - 1) * 30;
        assertThat(Paging.offset(Integer.MAX_VALUE)).isEqualTo(expected).isPositive();
    }

    /**
     * 【何のため】全部のページ数の計算を、境目（0件、1件、30件、31件）で確かめる。0件でも1ページとする。
     */
    @ParameterizedTest
    @CsvSource({ "0, 1", "1, 1", "29, 1", "30, 1", "31, 2", "60, 2", "61, 3", "300, 10", "301, 11" })
    void 全部のページ数は30件ごとに切り上げる(long totalCount, int expected) {
        assertThat(Paging.totalPages(totalCount)).isEqualTo(expected);
    }

    /**
     * 【何のため】一覧の返事（PageResponse）に、1ページの件数と全部のページ数が正しく入ることを確かめる。
     */
    @Test
    void 一覧の返事にページの情報が入る() {
        PageResponse<String> response = PageResponse.of(List.of("a", "b"), 31, 2);

        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.totalCount()).isEqualTo(31);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.pageSize()).isEqualTo(Paging.PAGE_SIZE);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    /**
     * 【何のため】検索の返事（SearchPageResponse）に、300件の上限を超えたかどうかが入ることを確かめる。
     */
    @Test
    void 検索の返事に上限を超えたかどうかが入る() {
        SearchPageResponse<String> response = SearchPageResponse.of(List.of(), 300, 11, true);

        assertThat(response.totalPages()).isEqualTo(10);
        assertThat(response.page()).isEqualTo(11);
        assertThat(response.limitExceeded()).isTrue();
    }
}

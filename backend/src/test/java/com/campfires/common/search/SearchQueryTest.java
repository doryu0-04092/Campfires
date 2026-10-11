package com.campfires.common.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 【役割】検索の条件（SearchQuery）の単体テスト。
 * 【なぜ必要か】検索のルール（要件定義書 5.8、5.11）のうち、
 * 「入力を単語に分ける（空白で区切る、100文字まで、5単語まで、小文字にそろえる）」と
 * 「300件の上限に合わせて、取り出す件数を減らす」計算を、境目の値で確かめるため。
 * 偽の API（frontend/src/mocks/search.ts）と同じ結果になる必要がある。
 */
class SearchQueryTest {

    /**
     * 【何のため】入力がない・空・空白だけのときは、単語がない（検索していない）ことを確かめる。
     * \t はタブ、　 は全角の空白。
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "   ", "\t", "　", " 　\t\n " })
    void 空や空白だけなら単語はない(String q) {
        assertThat(SearchQuery.splitWords(q)).isEmpty();
        assertThat(SearchQuery.of(q, null).isSearching()).isFalse();
    }

    /**
     * 【何のため】半角の空白、全角の空白、タブ、続けた空白のどれでも区切れること、前後の空白は無視することを確かめる。
     */
    @Test
    void 半角と全角の空白で単語に分ける() {
        assertThat(SearchQuery.splitWords("  Java　初心者\t\tもくもく会  "))
                .containsExactly("java", "初心者", "もくもく会");
    }

    /**
     * 【何のため】英字の大文字を小文字にそろえることを確かめる（大文字と小文字を区別しない検索のため）。
     */
    @Test
    void 英字は小文字にそろえる() {
        assertThat(SearchQuery.splitWords("SPRING Boot")).containsExactly("spring", "boot");
    }

    /**
     * 【何のため】6つ目以降の単語は使わないことを確かめる（ちょうど5つなら全部使う）。
     */
    @Test
    void 単語は5つまで使う() {
        assertThat(SearchQuery.splitWords("a b c d e")).containsExactly("a", "b", "c", "d", "e");
        assertThat(SearchQuery.splitWords("a b c d e f g")).containsExactly("a", "b", "c", "d", "e");
    }

    /**
     * 【何のため】ちょうど100文字なら全部使い、101文字目からは使わないことを確かめる。
     * "x".repeat(100) は「x を100個並べた文字」。
     */
    @Test
    void 入力は100文字まで使う() {
        String hundred = "x".repeat(SearchQuery.MAX_QUERY_LENGTH);
        assertThat(SearchQuery.splitWords(hundred)).containsExactly(hundred);
        assertThat(SearchQuery.splitWords(hundred + "y")).containsExactly(hundred);
        // 100文字目の境目をまたいだ単語は、途中で切れる。101文字目より後の単語は使わない
        assertThat(SearchQuery.splitWords("x".repeat(98) + " abc def")).containsExactly("x".repeat(98), "a");
    }

    /**
     * 【何のため】% や _ などの、SQL で特別な意味を持つ文字も、ふつうの文字としてそのまま残すことを確かめる
     * （SQL では LIKE ではなく strpos で探すので、そのまま渡してよい）。
     */
    @Test
    void 特別な文字はそのまま残す() {
        assertThat(SearchQuery.splitWords("100% a_b ' \\")).containsExactly("100%", "a_b", "'", "\\");
    }

    /**
     * 【何のため】検索していないときは、ページに関係なく、いつも30件ずつ取り出すことを確かめる。
     */
    @Test
    void 検索していないときは300件の上限がない() {
        SearchQuery query = SearchQuery.of(null, "20");

        assertThat(query.page()).isEqualTo(20);
        assertThat(query.offset()).isEqualTo(570);
        assertThat(query.limit()).isEqualTo(30);
        assertThat(query.totalCount(1000)).isEqualTo(1000);
        assertThat(query.limitExceeded(1000)).isFalse();
    }

    /**
     * 【何のため】検索したときは、10ページ目（271〜300件目）までは30件、11ページ目からは0件になることを確かめる。
     */
    @Test
    void 検索したときは300件目より後ろを取り出さない() {
        assertThat(SearchQuery.of("java", "10").limit()).isEqualTo(30);
        assertThat(SearchQuery.of("java", "10").offset()).isEqualTo(270);
        assertThat(SearchQuery.of("java", "11").limit()).isZero();
        // とても大きなページ番号でも、マイナスにならない
        assertThat(SearchQuery.of("java", String.valueOf(Integer.MAX_VALUE)).limit()).isZero();
    }

    /**
     * 【何のため】全部の件数は300件までにし、300件を超えたときだけ「上限を超えた」になることを確かめる（境目は300と301）。
     */
    @Test
    void 全部の件数は300件までで超えたら知らせる() {
        SearchQuery query = SearchQuery.of("java", null);

        assertThat(query.totalCount(299)).isEqualTo(299);
        assertThat(query.totalCount(300)).isEqualTo(300);
        assertThat(query.limitExceeded(300)).isFalse();
        assertThat(query.totalCount(301)).isEqualTo(300);
        assertThat(query.limitExceeded(301)).isTrue();
    }

    /**
     * 【何のため】ページ番号が変な値のときは、1ページ目として扱うことを確かめる。
     */
    @Test
    void ページ番号が変な値なら1ページ目() {
        SearchQuery query = SearchQuery.of("java", "abc");

        assertThat(query.page()).isEqualTo(1);
        assertThat(query.offset()).isZero();
        assertThat(query.limit()).isEqualTo(30);
    }
}

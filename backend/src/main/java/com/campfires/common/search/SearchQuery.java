package com.campfires.common.search;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.campfires.common.web.Paging;

/**
 * 【役割】検索のルール（要件定義書 5.8、5.11）のうち、「入力された文字を単語に分ける」と
 * 「何件目から何件取り出すか」の計算をまとめたクラス。イベントとコミュニティの検索で共通に使う。
 * 【なぜ必要か】検索のルールは、偽の API（frontend/src/mocks/search.ts）と同じにする必要がある。
 * ルールを1か所にまとめて、イベントとコミュニティで食い違わないようにする。
 *
 * 当てはまった単語の数を数える部分は、SQL（各 Mapper の XML）で行う。
 *
 * @param words  検索に使う単語の一覧（小文字にそろえたもの）。空なら「検索していない」
 * @param page   ページ番号
 * @param offset そのページの最初のデータが何件目か
 * @param limit  そのページで取り出す件数（検索したときは、300件目を超えない数）
 */
public record SearchQuery(List<String> words, int page, long offset, int limit) {

    // 入力できる文字数。超えた分は使わない
    public static final int MAX_QUERY_LENGTH = 100;
    // 使う単語の数。6つ目以降は使わない
    public static final int MAX_WORDS = 5;
    // 検索の結果の上限
    public static final int MAX_RESULTS = 300;

    /**
     * 【何のため】URL の q と page から、検索の条件を作る。
     */
    public static SearchQuery of(String q, String pageValue) {
        List<String> words = splitWords(q);
        int page = Paging.parsePage(pageValue);
        long offset = Paging.offset(page);
        int limit = Paging.PAGE_SIZE;
        if (!words.isEmpty()) {
            // 検索したときは、300件目より後ろは返さない。Math.max(0, …) でマイナスにならないようにする
            limit = (int) Math.max(0, Math.min(Paging.PAGE_SIZE, MAX_RESULTS - offset));
        }
        return new SearchQuery(words, page, offset, limit);
    }

    /**
     * 【何のため】入力された文字を、検索に使う単語の一覧に分ける。
     * 例：「 Java（全角の空白）初心者 」 → ["java", "初心者"]
     */
    public static List<String> splitWords(String q) {
        if (q == null) {
            return List.of();
        }
        // 100文字を超えた分は使わない
        String text = q.length() > MAX_QUERY_LENGTH ? q.substring(0, MAX_QUERY_LENGTH) : q;
        // 空白で区切る。\s は半角の空白やタブ、　 は全角の空白。+ は「1つ以上続く」
        return Arrays.stream(text.split("[\\s\\u3000]+"))
                // 区切った結果、空になったものを取り除く
                .filter(word -> !word.isEmpty())
                // 6つ目以降の単語は使わない
                .limit(MAX_WORDS)
                // 英字の大文字と小文字を区別しないように、すべて小文字にそろえる
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * 【何のため】検索しているかどうか（単語が1つ以上あるか）。
     */
    public boolean isSearching() {
        return !words.isEmpty();
    }

    /**
     * 【何のため】当てはまった件数から、返事に入れる「全部の件数」を求める（検索したときは300件まで）。
     */
    public long totalCount(long matchedCount) {
        return isSearching() ? Math.min(matchedCount, MAX_RESULTS) : matchedCount;
    }

    /**
     * 【何のため】300件の上限を超えたかどうか。
     */
    public boolean limitExceeded(long matchedCount) {
        return isSearching() && matchedCount > MAX_RESULTS;
    }
}

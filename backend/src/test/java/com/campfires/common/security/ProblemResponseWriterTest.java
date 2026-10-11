package com.campfires.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 【役割】Spring Security やフィルターが処理を止めたときに、エラーの返事を書き込む ProblemResponseWriter の単体テスト。
 * 【なぜ必要か】ここで書く返事も、ほかの API のエラーと同じ形（Problem Details、code 付き、日本語が文字化けしない）
 * でなければならない。Spring を起動せずに、返事の中身を細かく確かめる。
 */
class ProblemResponseWriterTest {

    /**
     * 【何のため】テストで使う JsonMapper（JSON に変える道具）を作る。
     * ProblemDetailJacksonMixin は、code などの追加の項目を、properties の中ではなく、いちばん外側に出すための設定。
     * Spring Boot が用意する JsonMapper と同じ形の JSON にするために付ける。
     */
    static JsonMapper jsonMapper() {
        return JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
    }

    /**
     * 【何のため】ステータスコード、Content-Type（application/problem+json）、文字コード、JSON の中身を確かめる。
     */
    @Test
    void ProblemDetailsの形で書き込む() throws Exception {
        JsonMapper mapper = jsonMapper();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ProblemResponseWriter(mapper).write(response, HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "送られてきたデータが大きすぎます。");

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
        // readTree は、JSON の文字を、項目をたどれる形（JsonNode）に変える
        JsonNode json = mapper.readTree(response.getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(413);
        assertThat(json.get("code").asString()).isEqualTo("PAYLOAD_TOO_LARGE");
        // 日本語が文字化けせずに入っていること
        assertThat(json.get("detail").asString()).isEqualTo("送られてきたデータが大きすぎます。");
        // 項目のエラーがないので、errors は入れない
        assertThat(json.has("errors")).isFalse();
    }
}

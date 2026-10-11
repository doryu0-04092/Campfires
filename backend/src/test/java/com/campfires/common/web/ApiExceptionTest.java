package com.campfires.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * 【役割】業務のエラー（ApiException）と、エラーの返事を作る道具（Problems）の単体テスト。
 * 【なぜ必要か】すべての API のエラーは、この2つのクラスで作られる。
 * ステータスコードと code の組み合わせ、項目ごとのエラー（errors）の有無が、決めた形（docs/api/README.md 1.5）
 * のとおりになることを確かめるため。
 */
class ApiExceptionTest {

    /**
     * 【何のため】よく使うエラーを作るメソッドが、決めたステータスコードと code を付けることを確かめる。
     */
    @Test
    void よく使うエラーはステータスとcodeが決まっている() {
        assertThat(ApiException.notFound("x").getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ApiException.notFound("x").getCode()).isEqualTo("NOT_FOUND");
        assertThat(ApiException.unauthenticated().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ApiException.unauthenticated().getCode()).isEqualTo("UNAUTHENTICATED");
        assertThat(ApiException.forbidden().getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ApiException.forbidden().getCode()).isEqualTo("FORBIDDEN");
        assertThat(ApiException.conflict("EVENT_FULL", "満席").getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ApiException.conflict("EVENT_FULL", "満席").getCode()).isEqualTo("EVENT_FULL");
        assertThat(ApiException.conflict("EVENT_FULL", "満席").getMessage()).isEqualTo("満席");
    }

    /**
     * 【何のため】1つの項目のエラー（badRequest）は、その項目のエラーを1つだけ持つことを確かめる。
     */
    @Test
    void badRequestは項目のエラーを1つ持つ() {
        ApiException exception = ApiException.badRequest("INVALID_PASSWORD", "password", "違います");

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getFieldErrors()).containsExactly(new FieldErrorItem("password", "違います"));
    }

    /**
     * 【何のため】作った後に、渡した一覧やヘッダーを外で書き換えても、エラーの中身は変わらないことを確かめる。
     * また、エラーの中の一覧は、書き換えられない（書き換えようとすると UnsupportedOperationException）ことも確かめる。
     */
    @Test
    void 渡した一覧を外で変えてもエラーの中身は変わらない() {
        List<FieldErrorItem> items = new ArrayList<>(List.of(new FieldErrorItem("title", "空です")));
        Map<String, String> headers = new HashMap<>(Map.of("Retry-After", "60"));
        ApiException exception = new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "誤り", items, headers);

        items.add(new FieldErrorItem("location", "空です"));
        headers.put("X-Other", "1");

        assertThat(exception.getFieldErrors()).hasSize(1);
        assertThat(exception.getHeaders()).containsExactly(Map.entry("Retry-After", "60"));
        // () -> … は「この処理を、あとで実行する」という書き方（ラムダ式）。投げられる例外の種類を確かめる
        assertThatThrownBy(() -> exception.getFieldErrors().add(new FieldErrorItem("a", "b")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 【何のため】エラーの返事に、status、detail、code が入り、項目のエラーがないときは errors を入れないことを確かめる。
     */
    @Test
    void 項目のエラーがなければerrorsを入れない() {
        ProblemDetail problem = Problems.of(ApiException.conflict("EVENT_FULL", "満席です。"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo("満席です。");
        assertThat(problem.getProperties()).containsEntry("code", "EVENT_FULL").doesNotContainKey("errors");
    }

    /**
     * 【何のため】項目のエラーがあるときは、errors に一覧が入ることを確かめる。
     */
    @Test
    void 項目のエラーがあればerrorsに入れる() {
        List<FieldErrorItem> errors = List.of(new FieldErrorItem("title", "空です"));
        ProblemDetail problem = Problems.of(ApiException.validation(errors));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_ERROR").containsEntry("errors", errors);
    }
}

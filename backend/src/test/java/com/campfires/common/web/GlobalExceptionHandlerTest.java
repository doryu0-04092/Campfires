package com.campfires.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 【役割】エラーを決まった形の返事に変える GlobalExceptionHandler の単体テスト。
 * 【なぜ必要か】API のテストでは、すべての分かれ道（405、415、406、入力のエラーが同じ項目に2つ、想定外のエラー）を
 * 通すのが難しい。このクラスのメソッドを直接呼んで、すべての分かれ道で、code が付き、
 * 内部の情報（例外の文など）が返事に入らないことを確かめる（OWASP Top 10 の A02）。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    // Spring の土台のクラスが受け取る「今のリクエスト」。テスト用の偽物（Mock）で作る
    private final ServletWebRequest webRequest =
            new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse());

    /** 【何のため】返事の中身を ProblemDetail として取り出す（中身の型が Object なので、変換する）。 */
    private static ProblemDetail body(ResponseEntity<?> response) {
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        return (ProblemDetail) response.getBody();
    }

    /** 【何のため】MethodParameter を作るためだけの、何もしないメソッド。 */
    @SuppressWarnings("unused")
    private void dummy(String value) {
    }

    /**
     * 【何のため】業務のエラーに付けたヘッダー（ロックのときの Retry-After など）が、返事に付くことを確かめる。
     */
    @Test
    void 業務のエラーのヘッダーが返事に付く() {
        ApiException exception = new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED", "ロック中",
                List.of(), Map.of("Retry-After", "120"));

        ResponseEntity<ProblemDetail> response = handler.handleApiException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("120");
        assertThat(response.getBody().getProperties()).containsEntry("code", "ACCOUNT_LOCKED");
    }

    /**
     * 【何のため】URL の番号が数でないとき（/api/communities/abc）は、404 NOT_FOUND になることを確かめる。
     */
    @Test
    void URLの番号が数でなければ404() {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException("abc", Long.class,
                "communityId", null, new NumberFormatException("For input string: abc"));

        ResponseEntity<ProblemDetail> response = handler.handleTypeMismatch(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getProperties()).containsEntry("code", "NOT_FOUND");
        assertThat(response.getBody().getDetail()).doesNotContain("abc");
    }

    /**
     * 【何のため】想定していないエラーのときは 500 にし、例外の文（SQL など）を返事に入れないことを確かめる。
     */
    @Test
    void 想定外のエラーは500で中身を返さない() {
        ResponseEntity<ProblemDetail> response =
                handler.handleUnexpected(new IllegalStateException("SELECT * FROM users WHERE password_hash = ..."));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(response.getBody().getDetail()).doesNotContain("SELECT", "password_hash");
    }

    /**
     * 【何のため】1つの項目が2つのルールに引っかかったときは、最初の1つだけを返すことを確かめる（偽の API と同じ）。
     */
    @Test
    void 同じ項目のエラーは最初の1つだけ返す() throws Exception {
        // BindingResult は「入力のチェックの結果」を入れる入れ物。FieldError は「どの項目の、どんなエラーか」
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "input");
        result.addError(new FieldError("input", "title", "タイトルを入力してください。"));
        result.addError(new FieldError("input", "title", "タイトルは100文字以内にしてください。"));
        result.addError(new FieldError("input", "location", "場所を入力してください。"));
        MethodParameter parameter =
                new MethodParameter(getClass().getDeclaredMethod("dummy", String.class), 0);

        ResponseEntity<Object> response =
                handler.handleException(new MethodArgumentNotValidException(parameter, result), webRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        ProblemDetail problem = body(response);
        assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_ERROR");
        assertThat(problem.getProperties().get("errors")).isEqualTo(List.of(
                new FieldErrorItem("title", "タイトルを入力してください。"),
                new FieldErrorItem("location", "場所を入力してください。")));
    }

    /**
     * 【何のため】JSON として読めないデータのときは 400 にし、読めなかった理由（内部の情報）を返さないことを確かめる。
     */
    @Test
    void 読めないJSONは400で理由を返さない() throws Exception {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character at com.campfires.event.EventInput",
                new MockHttpInputMessage(new byte[0]));

        ResponseEntity<Object> response = handler.handleException(exception, webRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        ProblemDetail problem = body(response);
        assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_ERROR");
        assertThat(problem.getDetail()).isEqualTo("送られてきたデータの形が正しくありません。");
    }

    /**
     * 【何のため】Spring 自身のエラーに、ステータスコードに合った code が付くことを確かめる。
     * 決めていないステータスコード（406）は ERROR にする。
     */
    @Test
    void Spring自身のエラーにもcodeが付く() throws Exception {
        ResponseEntity<Object> methodNotAllowed =
                handler.handleException(new HttpRequestMethodNotSupportedException("PUT"), webRequest);
        assertThat(methodNotAllowed.getStatusCode().value()).isEqualTo(405);
        assertThat(body(methodNotAllowed).getProperties()).containsEntry("code", "METHOD_NOT_ALLOWED");

        ResponseEntity<Object> unsupported = handler.handleException(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)),
                webRequest);
        assertThat(unsupported.getStatusCode().value()).isEqualTo(415);
        assertThat(body(unsupported).getProperties()).containsEntry("code", "UNSUPPORTED_MEDIA_TYPE");

        ResponseEntity<Object> notAcceptable =
                handler.handleException(new HttpMediaTypeNotAcceptableException("text/csv"), webRequest);
        assertThat(notAcceptable.getStatusCode().value()).isEqualTo(406);
        assertThat(body(notAcceptable).getProperties()).containsEntry("code", "ERROR");
    }

    /**
     * 【何のため】URL が見つからないときは 404 NOT_FOUND にし、Spring の英語の説明（URL を含む）を日本語に置き換えることを確かめる。
     */
    @Test
    void 見つからないURLは日本語の説明にする() throws Exception {
        ResponseEntity<Object> response =
                handler.handleException(new NoResourceFoundException(HttpMethod.GET, "/secret/path", "secret/path"),
                        webRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        ProblemDetail problem = body(response);
        assertThat(problem.getProperties()).containsEntry("code", "NOT_FOUND");
        assertThat(problem.getDetail()).isEqualTo("見つかりません。");
    }
}

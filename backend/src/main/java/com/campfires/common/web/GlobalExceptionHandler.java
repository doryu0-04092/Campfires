package com.campfires.common.web;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 【役割】API の処理の途中で投げられたエラー（例外）を受け止めて、決まった形のエラーの返事に変えるクラス。
 * 【なぜ必要か】Controller や Service ごとにエラーの返し方を書かずに、1か所でまとめて扱うため（docs/api/README.md 1.5）。
 * プログラムの内部の情報（SQL の文、クラスの名前など）は、攻撃の手がかりになるので、返事に入れない（OWASP Top 10 の A02）。
 *
 * ResponseEntityExceptionHandler は、Spring が用意している「Spring 自身のエラー（URL が見つからない、
 * 操作の種類が違うなど）を Problem Details にする」クラス。これを土台にして（extends）、
 * すべてのエラーに、このアプリ独自の code を付ける。
 *
 * @RestControllerAdvice は「すべての Controller に共通の処理」という印。
 * @ExceptionHandler(〜.class) を付けたメソッドが、その種類の例外を受け止める。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ログ（記録）を書くための道具。想定外のエラーの詳しい内容は、返事ではなくログに残す
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 【何のため】業務のルールのエラー（ApiException）を、そのまま返事にする。
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException exception) {
        // ヘッダー（Retry-After など）があれば、返事に付ける
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(exception.getStatus());
        exception.getHeaders().forEach((name, value) -> builder.header(name, value));
        return builder.body(Problems.of(exception));
    }

    /**
     * 【何のため】URL の番号が数でないとき（例：/api/communities/abc）に、見つからない（404）として返す。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return handleApiException(ApiException.notFound("見つかりません。"));
    }

    /**
     * 【何のため】想定していないエラーのときに、500 を返す。
     * 詳しい内容はログにだけ残し、返事には「エラーが起きた」ことだけを入れる。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("想定していないエラーが起きました", exception);
        return handleApiException(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "サーバーでエラーが起きました。時間をおいて、もう一度お試しください。"));
    }

    /**
     * 【何のため】入力のチェック（@Valid）で引っかかったときに、項目ごとのエラーを返す（400、VALIDATION_ERROR）。
     * 土台のクラスのメソッドを、上書き（@Override）している。
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // 引っかかった項目を1つずつ、FieldErrorItem に変える。stream() は一覧を1つずつ処理する書き方。
        // 1つの項目で複数のルールに引っかかったとき（例：空で、形も違う）は、最初の1つだけを返す（偽の API と同じ）。
        // filter(seen::add) は「初めて出てきた項目の名前なら残す」という意味（Set の add は、すでにあれば false を返す）
        Set<String> seen = new HashSet<>();
        List<FieldErrorItem> errors = exception.getBindingResult().getFieldErrors().stream()
                .filter(error -> seen.add(error.getField()))
                .map(error -> new FieldErrorItem(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(Problems.of(ApiException.validation(errors)));
    }

    /**
     * 【何のため】送られてきたデータが JSON として読めないとき（形が壊れているなど）に、400 を返す。
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "送られてきたデータの形が正しくありません。", List.of()));
    }

    /**
     * 【何のため】Spring 自身のエラー（URL が見つからない、操作の種類が違うなど）の返事に、code を付ける。
     * 土台のクラスは、すべての Spring 自身のエラーで、最後にこのメソッドを呼ぶ。
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // 先に土台のクラスに返事を作ってもらう（body が空のときは、土台のクラスが Problem Details の中身を作る）
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            // ステータスコードから、code を決める
            problem.setProperty("code", switch (status.value()) {
                case 400 -> "VALIDATION_ERROR";
                case 404 -> "NOT_FOUND";
                case 405 -> "METHOD_NOT_ALLOWED";
                case 415 -> "UNSUPPORTED_MEDIA_TYPE";
                default -> "ERROR";
            });
            // 404 のときは、Spring の英語の説明ではなく、日本語の説明にする
            if (status.value() == 404) {
                problem.setDetail("見つかりません。");
            }
        }
        return response;
    }
}

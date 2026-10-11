package com.campfires.common.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * 【役割】業務のルールに合わないときに投げる、エラーの種類（例外）。
 * 【なぜ必要か】Service（業務処理）の中で「満席です」「権限がありません」などの理由で処理を止めたいとき、
 * これを throw すると、GlobalExceptionHandler が受け止めて、決まった形のエラーの返事に変えてくれる。
 * code は docs/api/openapi.yaml の各 API の説明に書いた記号（例："EVENT_FULL"）を使う。
 *
 * RuntimeException を土台にしている（extends）。RuntimeException は、メソッドに throws を書かなくても投げられる例外。
 */
public class ApiException extends RuntimeException {

    // HTTP ステータスコード（例：409）
    private final HttpStatus status;
    // エラーの種類を表す記号（例："EVENT_FULL"）。画面はこれを見て表示を変える
    private final String code;
    // 入力のチェックで引っかかった項目の一覧（ないときは空）
    private final List<FieldErrorItem> fieldErrors;
    // 返事に付ける追加の情報（ヘッダー）。例：ロックのときの Retry-After（あと何秒で試せるか）
    private final Map<String, String> headers;

    /**
     * 【何のため】エラーを作る。message は利用者に見せてよい日本語の説明（返事の detail になる）。
     */
    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    /**
     * 【何のため】入力の項目ごとのエラーを付けて、エラーを作る。
     */
    public ApiException(HttpStatus status, String code, String message, List<FieldErrorItem> fieldErrors) {
        this(status, code, message, fieldErrors, Map.of());
    }

    /**
     * 【何のため】返事に付けるヘッダーも指定して、エラーを作る。
     */
    public ApiException(HttpStatus status, String code, String message, List<FieldErrorItem> fieldErrors,
            Map<String, String> headers) {
        // 土台の RuntimeException に、説明の文を渡す
        super(message);
        this.status = status;
        this.code = code;
        // List.copyOf は、あとから中身を変えられない一覧を作る（受け取った一覧が外で変えられても影響しない）
        this.fieldErrors = List.copyOf(fieldErrors);
        this.headers = Map.copyOf(headers);
    }

    // ---------- よく使うエラーを、短く作るためのメソッド ----------
    // static は「インスタンス（new したもの）を作らずに、クラスの名前から直接呼べる」という印

    /** 【何のため】404（見つからない）のエラーを作る。 */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    /**
     * 【何のため】401（ログインしていない）のエラーを作る。
     * セッションは残っているが、アカウントが削除されていたときなどに使う。
     */
    public static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "ログインしてください。");
    }

    /** 【何のため】403（権限がない）のエラーを作る。 */
    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "この操作をする権限がありません。");
    }

    /** 【何のため】409（今の状態ではできない）のエラーを作る。 */
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    /** 【何のため】400（入力が正しくない）のエラーを、1つの項目のエラー付きで作る。 */
    public static ApiException badRequest(String code, String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, List.of(new FieldErrorItem(field, message)));
    }

    /** 【何のため】入力のチェックのエラー（VALIDATION_ERROR）を、項目のエラーの一覧付きで作る。 */
    public static ApiException validation(List<FieldErrorItem> fieldErrors) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "入力内容に誤りがあります。", fieldErrors);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<FieldErrorItem> getFieldErrors() {
        return fieldErrors;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }
}

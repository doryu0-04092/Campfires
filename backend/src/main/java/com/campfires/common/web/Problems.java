package com.campfires.common.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * 【役割】エラーの返事（Problem Details。RFC 9457）を作る、共通のメソッドをまとめたクラス。
 * 【なぜ必要か】すべての API で、エラーの返事を同じ形にするため（docs/api/README.md 1.5）。
 * ProblemDetail は Spring が用意している、Problem Details の形のデータ。
 * 決まった項目（type、title、status、detail）に、このアプリ独自の code と errors を足す。
 *
 * final を付けたクラスは、ほかのクラスの土台（extends）にできない。メソッドを集めただけのクラスなので、そうしている。
 */
public final class Problems {

    // new Problems() でインスタンスを作れないようにする（static のメソッドだけを使うクラスのため）
    private Problems() {
    }

    /**
     * 【何のため】エラーの返事を作る。
     * @param status      HTTP ステータスコード
     * @param code        エラーの種類を表す記号
     * @param detail      利用者に見せてよい、日本語の説明
     * @param fieldErrors 入力のチェックで引っかかった項目（なければ空の一覧）
     */
    public static ProblemDetail of(HttpStatus status, String code, String detail, List<FieldErrorItem> fieldErrors) {
        // forStatusAndDetail で、status、title（ステータスコードの短い説明）、detail が入った ProblemDetail を作る
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        // setProperty で、決まった項目以外（code、errors）を足す
        problem.setProperty("code", code);
        if (!fieldErrors.isEmpty()) {
            problem.setProperty("errors", fieldErrors);
        }
        return problem;
    }

    /**
     * 【何のため】ApiException から、エラーの返事を作る。
     */
    public static ProblemDetail of(ApiException exception) {
        return of(exception.getStatus(), exception.getCode(), exception.getMessage(), exception.getFieldErrors());
    }
}

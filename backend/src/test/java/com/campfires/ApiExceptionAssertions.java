package com.campfires;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import com.campfires.common.web.ApiException;

/**
 * 【役割】「この処理は、決まったステータスコードと code の ApiException を投げる」ことを、1行で確かめるための道具。
 * 【なぜ必要か】業務の処理（Service）の単体テストでは、エラーの種類（code）を確かめることがとても多い。
 * 毎回同じ確かめ方を書かずに済むようにし、テストを読みやすくする。
 */
public final class ApiExceptionAssertions {

    private ApiExceptionAssertions() {
    }

    /**
     * 【何のため】処理を実行し、ApiException が投げられ、そのステータスコードと code が期待どおりかを確かめる。
     * 使い方：assertApiError(() -> service.apply(1, 2, 3), 409, "ALREADY_APPLIED");
     * ThrowingCallable は「例外を投げるかもしれない処理」を、あとで実行するために受け取る型（ラムダ式で渡す）。
     */
    public static void assertApiError(ThrowingCallable call, int status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus().value()).as("ステータスコード").isEqualTo(status);
            assertThat(exception.getCode()).as("code").isEqualTo(code);
        });
    }
}

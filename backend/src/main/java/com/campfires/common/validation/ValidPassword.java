package com.campfires.common.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 【役割】「新しく決めるパスワード」の入力のルール（8〜64文字、半角の英字・数字・記号だけ）を、1つの印にまとめたもの。
 * 【なぜ必要か】アカウントの登録（SignupRequest）、パスワードの変更（ChangePasswordRequest）、
 * パスワードの再設定（PasswordResetConfirmRequest）で、まったく同じルールを使う。
 * 3か所に同じルールを書くと、片方だけ直し忘れるおそれがあるので、ここに1つだけ書く。
 * ルールと文は、偽の API（frontend/src/mocks/handlers/validation.ts の password）と同じにしている。
 *
 * @interface は「印（アノテーション）を作る」書き方。下の @NotNull、@Size、@Pattern をまとめて付けたのと同じ意味になる
 * （Bean Validation の「組み合わせのルール」。@Constraint(validatedBy = {}) は「自分では確かめず、まとめたルールに任せる」）。
 * 引っかかったときは、まとめたルールそれぞれの message が、そのまま項目のエラーの文になる。
 * message、groups、payload の3つは、Bean Validation の決まりで、どの印にも必要な項目。
 */
@NotNull(message = "パスワードは8〜64文字にしてください。")
@Size(min = 8, max = 64, message = "パスワードは8〜64文字にしてください。")
// \x21-\x7E は、半角の英字・数字・記号（空白を除く）の範囲
@Pattern(regexp = "^[\\x21-\\x7E]*$", message = "パスワードには、半角の英字・数字・記号だけを使ってください。")
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = {})
public @interface ValidPassword {

    // まとめたルールの文を使うので、この文は表示されない（決まりで必要な項目）
    String message() default "パスワードは8〜64文字にしてください。";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

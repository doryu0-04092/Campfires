package com.campfires;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.campfires.account.UpdateMeRequest;
import com.campfires.auth.LoginRequest;
import com.campfires.auth.SignupRequest;
import com.campfires.community.CommunityInput;
import com.campfires.community.DecideJoinRequestRequest;
import com.campfires.community.UpdateMemberRoleRequest;
import com.campfires.event.DecideApplicationRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * 【役割】API に送られてくるデータ（〜Request、〜Input）に付けた、入力のルール（@Size、@Pattern など）の単体テスト。
 * 【なぜ必要か】文字数の境目（ニックネームの30文字と31文字、パスワードの8文字と64文字など）や、
 * パスワードに使える文字のルールを、API を通さずに、すばやく細かく確かめるため。
 * ルールと文は、偽の API（frontend/src/mocks）と同じにしているので、ずれたらここで気づける。
 */
class RequestRecordValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    /**
     * 【何のため】ルールに引っかかった項目の名前を、重複なしで返す（引っかからなければ空）。
     * Collectors.toSet() は、結果を Set（重複のない入れ物）に集める書き方。
     */
    private static Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    /** 【何のため】正しいアカウント登録のデータを作り、パスワードだけを差し替える。 */
    private static SignupRequest signupWithPassword(String password) {
        return new SignupRequest("山田 太郎", "たろう", "taro@example.com", password, true);
    }

    /**
     * 【何のため】パスワードの文字数の境目（7文字は通さない、8文字と64文字は通す、65文字は通さない）を確かめる。
     */
    @Test
    void パスワードは8文字から64文字まで() {
        assertThat(invalidFields(signupWithPassword("a".repeat(7)))).containsExactly("password");
        assertThat(invalidFields(signupWithPassword("a".repeat(8)))).isEmpty();
        assertThat(invalidFields(signupWithPassword("a".repeat(64)))).isEmpty();
        assertThat(invalidFields(signupWithPassword("a".repeat(65)))).containsExactly("password");
        assertThat(invalidFields(signupWithPassword(null))).containsExactly("password");
    }

    /**
     * 【何のため】パスワードには、半角の英字・数字・記号だけを使えることを確かめる（空白、全角の文字、日本語は通さない）。
     */
    @ParameterizedTest
    @ValueSource(strings = { "pass word1", "password１", "パスワードです12", "password\t1" })
    void パスワードは半角の英数字と記号だけ(String password) {
        assertThat(invalidFields(signupWithPassword(password))).containsExactly("password");
    }

    /**
     * 【何のため】記号をたくさん使ったパスワード（! から ~ までの半角の文字）は通すことを確かめる。
     */
    @Test
    void 半角の記号はパスワードに使える() {
        assertThat(invalidFields(signupWithPassword("!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"))).isEmpty();
    }

    /**
     * 【何のため】メールアドレスの形のルール（@ と「.」が必要、空白は使えない）と、254文字までの境目を確かめる。
     */
    @Test
    void メールアドレスの形と長さ() {
        assertThat(invalidFields(new SignupRequest("山田", "たろう", "a@b.c", "password", true))).isEmpty();
        assertThat(invalidFields(new SignupRequest("山田", "たろう", "a@b", "password", true))).containsExactly("email");
        assertThat(invalidFields(new SignupRequest("山田", "たろう", "a b@c.d", "password", true)))
                .containsExactly("email");
        // 254文字ちょうど（@example.com の12文字 + 242文字）は通し、255文字は通さない
        String local242 = "a".repeat(242);
        assertThat(invalidFields(new SignupRequest("山田", "たろう", local242 + "@example.com", "password", true)))
                .isEmpty();
        assertThat(invalidFields(new SignupRequest("山田", "たろう", local242 + "a@example.com", "password", true)))
                .containsExactly("email");
    }

    /**
     * 【何のため】本名は50文字まで、ニックネームは30文字までで、前後の空白は取り除いてから数えることを確かめる。
     */
    @Test
    void 本名は50文字ニックネームは30文字まで() {
        assertThat(invalidFields(new SignupRequest("あ".repeat(50), " " + "い".repeat(30) + " ", "a@b.c", "password",
                true))).isEmpty();
        assertThat(invalidFields(new SignupRequest("あ".repeat(51), "い".repeat(31), "a@b.c", "password", true)))
                .containsExactlyInAnyOrder("realName", "nickname");
        assertThat(invalidFields(new SignupRequest("　", " ", "a@b.c", "password", true)))
                .containsExactlyInAnyOrder("realName", "nickname");
    }

    /**
     * 【何のため】プライバシーポリシーと利用規約に同意していない（false か null）なら、登録できないことを確かめる。
     */
    @Test
    void 規約に同意しなければ登録できない() {
        assertThat(invalidFields(new SignupRequest("山田", "たろう", "a@b.c", "password", false)))
                .containsExactly("agreedToPolicies");
        assertThat(invalidFields(new SignupRequest("山田", "たろう", "a@b.c", "password", null)))
                .containsExactly("agreedToPolicies");
    }

    /**
     * 【何のため】ログインでは、パスワードの文字数や文字の種類を確かめない（空でなければよい）ことを確かめる。
     * 登録のルールを、ログインで教えてしまわないようにするため。メールアドレスの前後の空白は取り除く。
     */
    @Test
    void ログインは空でなければよい() {
        assertThat(invalidFields(new LoginRequest(" a@b.c ", "x"))).isEmpty();
        assertThat(new LoginRequest(" a@b.c ", "x").email()).isEqualTo("a@b.c");
        assertThat(invalidFields(new LoginRequest(" ", ""))).containsExactlyInAnyOrder("email", "password");
    }

    /**
     * 【何のため】ニックネームの変更は、1文字から30文字までであることを確かめる。
     */
    @Test
    void ニックネームの変更は30文字まで() {
        assertThat(invalidFields(new UpdateMeRequest("い".repeat(30)))).isEmpty();
        assertThat(invalidFields(new UpdateMeRequest("い".repeat(31)))).containsExactly("nickname");
        assertThat(invalidFields(new UpdateMeRequest("  "))).containsExactly("nickname");
    }

    /**
     * 【何のため】コミュニティの名前は50文字まで、説明は2000文字まで（説明は空でもよい）であることを確かめる。
     */
    @Test
    void コミュニティの名前は50文字で説明は2000文字まで() {
        assertThat(invalidFields(new CommunityInput("あ".repeat(50), "い".repeat(2000)))).isEmpty();
        assertThat(invalidFields(new CommunityInput("あ".repeat(51), "い".repeat(2001))))
                .containsExactlyInAnyOrder("name", "description");
        assertThat(new CommunityInput(" 名前 ", null).description()).isEmpty();
    }

    /**
     * 【何のため】承認・却下、立場の変更で送れる値が、決まった値だけであることを確かめる（大文字や前後の空白も通さない）。
     */
    @Test
    void 決まった値しか送れない() {
        assertThat(invalidFields(new DecideApplicationRequest("approved"))).isEmpty();
        assertThat(invalidFields(new DecideApplicationRequest("rejected"))).isEmpty();
        assertThat(invalidFields(new DecideApplicationRequest("APPROVED"))).containsExactly("status");
        assertThat(invalidFields(new DecideApplicationRequest(null))).containsExactly("status");
        assertThat(invalidFields(new DecideJoinRequestRequest("pending"))).containsExactly("status");
        assertThat(invalidFields(new UpdateMemberRoleRequest("staff"))).isEmpty();
        assertThat(invalidFields(new UpdateMemberRoleRequest("owner"))).containsExactly("role");
        assertThat(invalidFields(new UpdateMemberRoleRequest(" member"))).containsExactly("role");
    }
}

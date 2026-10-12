package com.campfires.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 【役割】パスワードの再設定（F-25）の API（docs/api/openapi.yaml の auth の password-reset-requests、password-resets）。
 * 【なぜ必要か】画面から届いたデータを受け取り、PasswordResetService に処理を頼み、結果を返事にするため。
 * どちらも、ログインしていなくても呼べる（パスワードを忘れた人が使うため。SecurityConfig の /api/auth/**）。
 * 同じ IP アドレスからの依頼の回数は、RateLimitFilter で制限している（1時間に5回まで）。
 */
@RestController
public class PasswordResetController {

    // 依頼の返事の文。登録されているかどうかに関係なく、いつも同じ文を返す（メールアドレスを探られないように）
    static final String ACCEPTED_MESSAGE =
            "登録されているメールアドレスなら、パスワードの再設定のメールを送りました。メールのリンクから、30分以内に再設定してください。";

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * 【何のため】パスワードの再設定の返事の形。
     * @param message 画面に表示してよい文
     */
    public record AcceptedResponse(String message) {
    }

    /**
     * 【何のため】パスワードの再設定を依頼する（POST /api/auth/password-reset-requests）。
     * 登録されていても、いなくても、同じ返事（202、受け付けた）を返す。
     */
    @PostMapping("/api/auth/password-reset-requests")
    public ResponseEntity<AcceptedResponse> requestReset(@Valid @RequestBody PasswordResetRequest body) {
        passwordResetService.requestReset(body.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new AcceptedResponse(ACCEPTED_MESSAGE));
    }

    /**
     * 【何のため】リンクのトークンと新しいパスワードで、パスワードを決め直す（POST /api/auth/password-resets）。
     * 成功したら 204（返す中身なし）。自動ではログインしない（画面はログインの画面へ案内する）。
     */
    @PostMapping("/api/auth/password-resets")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest body) {
        passwordResetService.resetPassword(body.token(), body.newPassword());
        return ResponseEntity.noContent().build();
    }
}

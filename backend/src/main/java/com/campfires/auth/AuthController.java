package com.campfires.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.campfires.account.MeResponse;
import com.campfires.account.User;
import com.campfires.common.security.LoginSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 【役割】アカウントの登録とログイン、使える機能の一覧の API（docs/api/openapi.yaml の auth）。
 * パスワードの再設定の API は、PasswordResetController に分けている。
 * 【なぜ必要か】画面から届いたデータを受け取り、AuthService に処理を頼み、結果を返事にするため。
 * ログアウト（POST /api/auth/logout）は、Spring Security の設定（SecurityConfig）で行っている。
 *
 * @RequestBody は「送られてきた JSON を、この引数に入れる」、@Valid は「入力のチェックをする」という印。
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final LoginSession loginSession;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, LoginSession loginSession,
            PasswordResetService passwordResetService) {
        this.authService = authService;
        this.loginSession = loginSession;
        this.passwordResetService = passwordResetService;
    }

    /**
     * 【何のため】ログインまわりの機能のうち、今使えるものの一覧の返事の形（docs/api/openapi.yaml の AuthFeatures）。
     * @param passwordResetEnabled パスワードの再設定（パスワードを忘れた場合）を使えるかどうか
     */
    public record AuthFeatures(boolean passwordResetEnabled) {
    }

    /**
     * 【何のため】今使える機能を返す（GET /api/auth/features）。ログインしていなくても呼べる。
     * 画面は、これを見て「パスワードを忘れた場合」のリンクを出すかどうかを決める
     * （メールを送る仕組みがない本番では、再設定を止めているため）。
     */
    @GetMapping("/api/auth/features")
    public AuthFeatures features() {
        return new AuthFeatures(passwordResetService.isEnabled());
    }

    /**
     * 【何のため】アカウントを登録する（POST /api/auth/signup）。成功したら、そのままログインした状態にする（201）。
     */
    @PostMapping("/api/auth/signup")
    public ResponseEntity<MeResponse> signup(@Valid @RequestBody SignupRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        User user = authService.signup(body);
        loginSession.start(user.id(), request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(MeResponse.from(user));
    }

    /**
     * 【何のため】ログインする（POST /api/auth/login）。
     */
    @PostMapping("/api/auth/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        User user = authService.login(body);
        loginSession.start(user.id(), request, response);
        return MeResponse.from(user);
    }
}

package com.campfires.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * 【役割】アカウントの登録とログインの API（docs/api/openapi.yaml の auth）。
 * 【なぜ必要か】画面から届いたデータを受け取り、AuthService に処理を頼み、結果を返事にするため。
 * ログアウト（POST /api/auth/logout）は、Spring Security の設定（SecurityConfig）で行っている。
 *
 * @RequestBody は「送られてきた JSON を、この引数に入れる」、@Valid は「入力のチェックをする」という印。
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final LoginSession loginSession;

    public AuthController(AuthService authService, LoginSession loginSession) {
        this.authService = authService;
        this.loginSession = loginSession;
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

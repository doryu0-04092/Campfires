package com.campfires.account;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.campfires.common.security.LoginSession;
import com.campfires.common.security.LoginUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 【役割】自分のアカウントの API（docs/api/openapi.yaml の account の /api/me）。
 * 【なぜ必要か】画面から届いたデータを受け取り、AccountService に処理を頼み、結果を返事にするため。
 * どの API もログインが必要（SecurityConfig で、ログインしていなければ 401 にしている）。
 *
 * @AuthenticationPrincipal は「今ログインしている人（LoginUser）を、この引数に入れる」という印。
 */
@RestController
public class AccountController {

    private final AccountService accountService;
    private final LoginSession loginSession;

    public AccountController(AccountService accountService, LoginSession loginSession) {
        this.accountService = accountService;
        this.loginSession = loginSession;
    }

    /**
     * 【何のため】自分の情報を返す（GET /api/me）。画面は、これでログインしているかどうかを判断する。
     */
    @GetMapping("/api/me")
    public MeResponse getMe(@AuthenticationPrincipal LoginUser loginUser) {
        return MeResponse.from(accountService.getMe(loginUser.id()));
    }

    /**
     * 【何のため】ニックネームを変える（PATCH /api/me）。変えた後の情報を返す。
     */
    @PatchMapping("/api/me")
    public MeResponse updateMe(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody UpdateMeRequest body) {
        return MeResponse.from(accountService.updateNickname(loginUser.id(), body.nickname()));
    }

    /**
     * 【何のため】アカウントを削除する（DELETE /api/me）。削除したら、ログアウトした状態にする（204）。
     */
    @DeleteMapping("/api/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody DeleteMeRequest body, HttpServletRequest request, HttpServletResponse response) {
        accountService.delete(loginUser.id(), body.password());
        loginSession.end(request, response);
        return ResponseEntity.noContent().build();
    }
}

package com.campfires.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【役割】CSRF トークンを受け取る API（GET /api/auth/csrf）。
 * 【なぜ必要か】画面は、データを変える API を呼ぶ前に、CSRF トークン（合言葉）を持っている必要がある。
 * 画面を開いたときに最初にこの API を呼ぶと、XSRF-TOKEN の Cookie で合言葉が届く（docs/api/README.md 1.7）。
 *
 * @RestController は「API を受け付けるクラス（返事は JSON などのデータ）」という印。
 */
@RestController
public class CsrfController {

    /**
     * 【何のため】CSRF トークンを作り、Cookie で返す。返す中身はない（204）。
     * 引数の CsrfToken は、Spring Security が用意してくれる。
     * getToken() で中身を読むと、合言葉が作られて、返事に XSRF-TOKEN の Cookie が付く。
     */
    @GetMapping("/api/auth/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }
}

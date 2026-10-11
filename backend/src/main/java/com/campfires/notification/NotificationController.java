package com.campfires.notification;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.campfires.common.security.LoginUser;

/**
 * 【役割】お知らせの API（docs/api/openapi.yaml の notifications）。
 * 【なぜ必要か】画面から届いたデータを受け取り、NotificationService に処理を頼み、結果を返事にするため。
 * どの API もログインが必要で、ログインしている人あてのお知らせだけを扱う。
 */
@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 【何のため】お知らせの一覧（GET /api/notifications）。
     */
    @GetMapping("/api/notifications")
    public NotificationListResponse list(@AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) String page) {
        return notificationService.list(loginUser.id(), page);
    }

    /**
     * 【何のため】未読のお知らせの件数（GET /api/notifications/unread-count）。
     */
    @GetMapping("/api/notifications/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal LoginUser loginUser) {
        return notificationService.unreadCount(loginUser.id());
    }

    /**
     * 【何のため】お知らせを1件、既読にする（PUT /api/notifications/{notificationId}/read）。
     */
    @PutMapping("/api/notifications/{notificationId}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable long notificationId) {
        return notificationService.markRead(loginUser.id(), notificationId);
    }

    /**
     * 【何のため】お知らせをすべて既読にする（POST /api/notifications/read-all）。204 を返す。
     */
    @PostMapping("/api/notifications/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal LoginUser loginUser) {
        notificationService.markAllRead(loginUser.id());
        return ResponseEntity.noContent().build();
    }
}

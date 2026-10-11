package com.campfires.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campfires.common.web.ApiException;
import com.campfires.common.web.Paging;

/**
 * 【役割】お知らせの一覧と既読の、業務の処理をするクラス。
 * 【なぜ必要か】要件定義書 5.10 のルール（自分あてのものだけ、既読は read_at で管理する）を、1か所にまとめるため。
 */
@Service
public class NotificationService {

    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * 【何のため】自分あてのお知らせを、新しい順に返す。未読の件数も一緒に返す。
     */
    public NotificationListResponse list(long userId, String pageValue) {
        int page = Paging.parsePage(pageValue);
        long total = notificationMapper.countByUser(userId);
        var items = notificationMapper.findByUser(userId, Paging.PAGE_SIZE, Paging.offset(page)).stream()
                .map(NotificationRow::toResponse)
                .toList();
        return NotificationListResponse.of(items, total, page, notificationMapper.countUnread(userId));
    }

    /**
     * 【何のため】未読のお知らせの件数を返す（画面上部の表示に使う）。
     */
    public UnreadCountResponse unreadCount(long userId) {
        return new UnreadCountResponse(notificationMapper.countUnread(userId));
    }

    /**
     * 【何のため】お知らせを1件、既読にする。何度呼んでも結果は同じ。
     * 他人あてのお知らせは、存在しない場合と同じく 404 にする（他人あてのお知らせがあることを知られないため）。
     */
    @Transactional
    public NotificationResponse markRead(long userId, long notificationId) {
        if (notificationMapper.findOne(userId, notificationId) == null) {
            throw ApiException.notFound("お知らせが見つかりません。");
        }
        notificationMapper.markRead(userId, notificationId);
        return notificationMapper.findOne(userId, notificationId).toResponse();
    }

    /**
     * 【何のため】自分あてのお知らせを、すべて既読にする。
     */
    @Transactional
    public void markAllRead(long userId) {
        notificationMapper.markAllRead(userId);
    }
}

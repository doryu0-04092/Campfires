package com.campfires.notification;

import static com.campfires.ApiExceptionAssertions.assertApiError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 【役割】お知らせの一覧・既読の業務の処理（NotificationService）と、お知らせの行を返事の形に変える処理の単体テスト。
 * 【なぜ必要か】他人あてのお知らせを、既読にできてしまわないこと（見つからないものとして 404）と、
 * 出来事を起こした人がいないお知らせ（イベントの削除など）で actor が null になることを確かめるため。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final long USER = 3;

    @Mock
    private NotificationMapper notificationMapper;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationMapper);
    }

    /**
     * 【何のため】自分あてでない（見つからない）お知らせは、既読にせずに 404 にすることを確かめる。
     */
    @Test
    void 自分あてでないお知らせは既読にしない() {
        assertApiError(() -> service.markRead(USER, 99), 404, "NOT_FOUND");

        verify(notificationMapper, never()).markRead(anyLong(), anyLong());
    }

    /**
     * 【何のため】既読にしたら、読み直して、既読になったお知らせを返すことを確かめる。
     */
    @Test
    void 既読にしたら読み直して返す() {
        when(notificationMapper.findOne(USER, 1))
                .thenReturn(new NotificationRow(1, "event_deleted", "t", 2, null, null, null, null, Instant.EPOCH))
                .thenReturn(new NotificationRow(1, "event_deleted", "t", 2, null, null, null, Instant.EPOCH,
                        Instant.EPOCH));

        NotificationResponse response = service.markRead(USER, 1);

        verify(notificationMapper).markRead(USER, 1);
        assertThat(response.read()).isTrue();
    }

    /**
     * 【何のため】一覧には、全部の件数と未読の件数が入り、2ページ目なら31件目から取り出すことを確かめる。
     */
    @Test
    void 一覧に未読の件数を入れる() {
        when(notificationMapper.countByUser(USER)).thenReturn(45L);
        when(notificationMapper.countUnread(USER)).thenReturn(4L);

        NotificationListResponse response = service.list(USER, "2");

        verify(notificationMapper).findByUser(USER, 30, 30L);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.unreadCount()).isEqualTo(4);
    }

    /**
     * 【何のため】出来事を起こした人がいれば actor に入れ、いなければ actor を null にすることを確かめる。
     * 既読の日時があれば read は true。
     */
    @Test
    void 起こした人がいなければactorはnull() {
        NotificationResponse withActor =
                new NotificationRow(1, "late_cancellation", "t", 2, 3L, 4L, "たろう", null, Instant.EPOCH).toResponse();
        NotificationResponse withoutActor =
                new NotificationRow(1, "event_deleted", "t", 2, null, null, null, Instant.EPOCH, Instant.EPOCH)
                        .toResponse();

        assertThat(withActor.actor().userId()).isEqualTo(4);
        assertThat(withActor.actor().nickname()).isEqualTo("たろう");
        assertThat(withActor.read()).isFalse();
        assertThat(withoutActor.actor()).isNull();
        assertThat(withoutActor.read()).isTrue();
    }
}

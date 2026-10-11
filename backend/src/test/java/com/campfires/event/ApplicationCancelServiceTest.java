package com.campfires.event;

import static com.campfires.event.EventRowFixtures.EVENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.campfires.notification.NotificationMapper;

/**
 * 【役割】申込みを取り消して、その後の処理（繰り上げ、お知らせ）を行う ApplicationCancelService の単体テスト。
 * 【なぜ必要か】取り消しの後の処理は「承認待ちか × 締め切りの前か後か × 申込み済みかキャンセル待ちか」で変わる
 * （要件定義書 5.2、5.3、5.12）。キャンセル、退会、除名、アカウントの削除のすべてがここを通るので、
 * すべての組み合わせを確かめる。
 */
@ExtendWith(MockitoExtension.class)
class ApplicationCancelServiceTest {

    // 取り消される人、取り消しの操作をした人（除名のときのオーナーなど）の番号
    private static final long USER = 5;
    private static final long OPERATOR = 6;

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private NotificationMapper notificationMapper;

    private ApplicationCancelService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationCancelService(applicationMapper, notificationMapper);
    }

    /** 【何のため】取り消す申込みを、偽物に教える。 */
    private void givenTarget(String status, boolean deadlinePassed) {
        when(applicationMapper.findCancelTarget(EVENT_ID, USER))
                .thenReturn(new CancelTarget(99, EVENT_ID, USER, status, deadlinePassed));
    }

    /**
     * 【何のため】申込みがなければ false を返し、何も削除しないことを確かめる。
     */
    @Test
    void 申込みがなければ何もしない() {
        assertThat(service.cancel(EVENT_ID, USER, USER)).isFalse();

        verify(applicationMapper, never()).delete(anyLong());
    }

    /**
     * 【何のため】先にイベントの行をロックしてから、申込みを探して削除することを確かめる（順番が大事）。
     */
    @Test
    void 先にイベントをロックする() {
        givenTarget("applied", false);

        service.cancel(EVENT_ID, USER, USER);

        InOrder order = inOrder(applicationMapper);
        order.verify(applicationMapper).lockEvent(EVENT_ID);
        order.verify(applicationMapper).findCancelTarget(EVENT_ID, USER);
        order.verify(applicationMapper).delete(99);
        order.verify(applicationMapper).promoteWaitlist(EVENT_ID);
    }

    /**
     * 【何のため】承認待ちは席を使っていないので、締め切りの前でも後でも、繰り上げもお知らせもしないことを確かめる。
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void 承認待ちの取り消しは繰り上げもお知らせもしない(boolean deadlinePassed) {
        givenTarget("pending", deadlinePassed);

        assertThat(service.cancel(EVENT_ID, USER, USER)).isTrue();

        verify(applicationMapper).delete(99);
        verify(applicationMapper, never()).promoteWaitlist(anyLong());
        verify(notificationMapper, never()).insertLateCancellation(anyLong(), anyLong(), anyLong());
    }

    /**
     * 【何のため】締め切り後の取り消しは、繰り上げずに、オーナーと運営スタッフにお知らせを送ることを確かめる。
     * キャンセル待ちの人の取り消しでも、お知らせを送る。操作した人は、お知らせの対象から外す（SQL に渡す）。
     */
    @ParameterizedTest
    @ValueSource(strings = { "applied", "waitlisted" })
    void 締め切り後は繰り上げずに知らせる(String status) {
        givenTarget(status, true);

        service.cancel(EVENT_ID, USER, OPERATOR);

        verify(notificationMapper).insertLateCancellation(EVENT_ID, USER, OPERATOR);
        verify(applicationMapper, never()).promoteWaitlist(anyLong());
    }

    /**
     * 【何のため】締め切り前に申込み済みの人が取り消したら、キャンセル待ちを繰り上げ、お知らせは送らないことを確かめる。
     */
    @Test
    void 締め切り前に申込み済みが取り消すと繰り上げる() {
        givenTarget("applied", false);

        service.cancel(EVENT_ID, USER, USER);

        verify(applicationMapper).promoteWaitlist(EVENT_ID);
        verify(notificationMapper, never()).insertLateCancellation(anyLong(), anyLong(), anyLong());
    }

    /**
     * 【何のため】締め切り前にキャンセル待ちの人が取り消しても、席は空かないので、繰り上げないことを確かめる。
     */
    @Test
    void 締め切り前にキャンセル待ちが取り消しても繰り上げない() {
        givenTarget("waitlisted", false);

        service.cancel(EVENT_ID, USER, USER);

        verify(applicationMapper, never()).promoteWaitlist(anyLong());
        verify(notificationMapper, never()).insertLateCancellation(anyLong(), anyLong(), anyLong());
    }

    /**
     * 【何のため】まとめて取り消すとき、SQL が返したイベントの順番（番号の小さい順）に、1件ずつ取り消すことを確かめる
     * （ロックを取る順番をそろえて、デッドロックを防ぐため）。
     */
    @Test
    void まとめて取り消すときはイベントの番号の順() {
        when(applicationMapper.findUpcomingEventIdsOfUser(USER, 3L)).thenReturn(List.of(11L, 12L, 15L));

        service.cancelUpcomingApplications(USER, OPERATOR, 3L);

        InOrder order = inOrder(applicationMapper);
        order.verify(applicationMapper).lockEvent(11);
        order.verify(applicationMapper).lockEvent(12);
        order.verify(applicationMapper).lockEvent(15);
    }
}

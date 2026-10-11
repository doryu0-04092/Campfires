package com.campfires.event;

import static com.campfires.event.EventRowFixtures.COMMUNITY_ID;
import static com.campfires.event.EventRowFixtures.EVENT_ID;
import static com.campfires.event.EventRowFixtures.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.campfires.notification.NotificationMapper;

/**
 * 【役割】承認待ちの申込みを承認・却下する ApplicationApprovalService の単体テスト。
 * 【なぜ必要か】承認したときに「空きがあれば申込み済み、満席ならキャンセル待ち」を決めるルールと、
 * 「手動から自動に変えたら、申し込んだ順にまとめて承認する（1人ずつ人数を数え直す）」ルール（要件定義書 5.12）を確かめるため。
 */
@ExtendWith(MockitoExtension.class)
class ApplicationApprovalServiceTest {

    @Mock
    private EventMapper eventMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private NotificationMapper notificationMapper;

    private ApplicationApprovalService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationApprovalService(eventMapper, applicationMapper, notificationMapper);
    }

    /**
     * 【何のため】空きがあれば申込み済みにして、承認のお知らせを送ることを確かめる（残り1枠の境目）。
     */
    @Test
    void 空きがあれば申込み済みにして知らせる() {
        when(eventMapper.find(COMMUNITY_ID, EVENT_ID)).thenReturn(row(10, 9, "manual", "closed"));
        when(applicationMapper.approve(EVENT_ID, 5, "applied")).thenReturn(1);

        assertThat(service.approve(COMMUNITY_ID, EVENT_ID, 5)).isTrue();
        verify(notificationMapper).insertApplicationDecided(EVENT_ID, 5, "application_approved");
    }

    /**
     * 【何のため】満席なら、承認してもキャンセル待ちになることを確かめる（承認のお知らせは送る）。
     */
    @Test
    void 満席ならキャンセル待ちにする() {
        when(eventMapper.find(COMMUNITY_ID, EVENT_ID)).thenReturn(row(10, 10, "manual", "closed"));
        when(applicationMapper.approve(EVENT_ID, 5, "waitlisted")).thenReturn(1);

        assertThat(service.approve(COMMUNITY_ID, EVENT_ID, 5)).isTrue();
        verify(notificationMapper).insertApplicationDecided(EVENT_ID, 5, "application_approved");
    }

    /**
     * 【何のため】承認待ちでなければ（書き換えた行が0）、false を返し、お知らせを送らないことを確かめる。
     */
    @Test
    void 承認待ちでなければ何もしない() {
        when(eventMapper.find(COMMUNITY_ID, EVENT_ID)).thenReturn(row(10, 0, "manual", "closed"));

        assertThat(service.approve(COMMUNITY_ID, EVENT_ID, 5)).isFalse();
        assertThat(service.reject(EVENT_ID, 5)).isFalse();
        verify(notificationMapper, never()).insertApplicationDecided(anyLong(), anyLong(), anyString());
    }

    /**
     * 【何のため】却下したら、却下のお知らせを送ることを確かめる。
     */
    @Test
    void 却下したら知らせる() {
        when(applicationMapper.reject(EVENT_ID, 5)).thenReturn(1);

        assertThat(service.reject(EVENT_ID, 5)).isTrue();
        verify(notificationMapper).insertApplicationDecided(EVENT_ID, 5, "application_rejected");
    }

    /**
     * 【何のため】まとめて承認するとき、申し込んだ順に1人ずつ、そのたびに人数を読み直して承認することを確かめる。
     * 残り1枠なら、1人目は申込み済み、2人目はキャンセル待ちになる。
     * InOrder は「呼ばれた順番」を確かめる Mockito の道具。
     */
    @Test
    void まとめて承認するときは1人ずつ人数を読み直す() {
        when(applicationMapper.findPending(EVENT_ID, true))
                .thenReturn(List.of(new PendingApplication(7), new PendingApplication(8)));
        // 1回目に読んだときは残り1枠、2回目に読んだときは満席
        when(eventMapper.find(COMMUNITY_ID, EVENT_ID))
                .thenReturn(row(10, 9, "auto", "closed"))
                .thenReturn(row(10, 10, "auto", "closed"));
        when(applicationMapper.approve(EVENT_ID, 7, "applied")).thenReturn(1);
        when(applicationMapper.approve(EVENT_ID, 8, "waitlisted")).thenReturn(1);

        service.approveAllPending(COMMUNITY_ID, EVENT_ID, true);

        InOrder order = inOrder(applicationMapper);
        order.verify(applicationMapper).approve(EVENT_ID, 7, "applied");
        order.verify(applicationMapper).approve(EVENT_ID, 8, "waitlisted");
    }

    /**
     * 【何のため】メンバー以外の申込みをまとめて承認するときは、メンバー以外の承認待ちだけを探すことを確かめる。
     */
    @Test
    void メンバー以外だけをまとめて承認できる() {
        service.approveAllPending(COMMUNITY_ID, EVENT_ID, false);

        verify(applicationMapper).findPending(EVENT_ID, false);
        verify(applicationMapper, never()).findPending(EVENT_ID, true);
    }
}

package com.campfires.event;

import static com.campfires.ApiExceptionAssertions.assertApiError;
import static com.campfires.event.EventRowFixtures.COMMUNITY_ID;
import static com.campfires.event.EventRowFixtures.EVENT_ID;
import static com.campfires.event.EventRowFixtures.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.campfires.common.web.ApiException;
import com.campfires.community.Community;
import com.campfires.community.CommunityGuard;

/**
 * 【役割】イベントへの申込み・取り消し・承認・却下の業務の処理（ApplicationService）の単体テスト。
 * 【なぜ必要か】申込みのルール（要件定義書 5.2、5.3、5.12）は分かれ道が多い。
 * 「メンバーかどうか × 承認の方法（自動・手動・受け付けない）× 空きがあるか × 締め切り × すでに申し込んでいるか」
 * のすべての分かれ道と、エラーを確かめる順番を、データベースなしで確かめる。
 * 定員を同時に超えないしくみ（ロック）は、ConcurrentApplicationTest で本物のデータベースを使って確かめている。
 */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    // 申し込む人、オーナー（管理する人）の番号
    private static final long USER = 50;
    private static final long MANAGER = 60;

    @Mock
    private EventService eventService;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ApplicationCancelService cancelService;
    @Mock
    private ApplicationApprovalService approvalService;
    @Mock
    private CommunityGuard guard;

    private ApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(eventService, applicationMapper, cancelService, approvalService, guard);
    }

    /** 【何のため】イベントと、申し込む人の立場（null ならメンバー以外）を、偽物に教える。 */
    private void given(EventRow event, String role) {
        when(eventService.requireEventForUpdate(COMMUNITY_ID, EVENT_ID)).thenReturn(event);
        when(guard.roleOf(COMMUNITY_ID, USER)).thenReturn(role);
    }

    /** 【何のため】申し込み、登録しようとした状態（applied など）を返す。 */
    private String applyAndCaptureStatus() {
        when(applicationMapper.findMyApplication(EVENT_ID, USER))
                .thenReturn(null)
                .thenReturn(new MyApplication("any", null, Instant.EPOCH));
        service.apply(USER, COMMUNITY_ID, EVENT_ID);
        // ArgumentCaptor は「偽物に渡された値を、あとから取り出す」道具。登録に使った状態を取り出す
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(applicationMapper).insert(eq(EVENT_ID), eq(USER), status.capture());
        return status.getValue();
    }

    // ---------- 申込み ----------

    /**
     * 【何のため】自動のときは、空きがあれば申込み済み、残り0枠ならキャンセル待ちになることを、境目（残り1枠と0枠）で確かめる。
     */
    @Test
    void 自動なら空きがあれば申込み済み満席ならキャンセル待ち() {
        given(row(10, 9, "auto", "closed"), "member");
        assertThat(applyAndCaptureStatus()).isEqualTo("applied");
    }

    /** 【何のため】定員ちょうどまで埋まっていれば、キャンセル待ちになることを確かめる。 */
    @Test
    void 満席ならキャンセル待ち() {
        given(row(10, 10, "auto", "closed"), "member");
        assertThat(applyAndCaptureStatus()).isEqualTo("waitlisted");
    }

    /**
     * 【何のため】手動のときは、空きがあっても満席でも、承認待ちになることを確かめる（席は承認したときに取る）。
     */
    @Test
    void 手動なら満席でも承認待ち() {
        given(row(10, 10, "manual", "closed"), "staff");
        assertThat(applyAndCaptureStatus()).isEqualTo("pending");
    }

    /**
     * 【何のため】メンバー以外は nonMemberApproval を使うことを確かめる（メンバーは自動でも、メンバー以外は手動）。
     */
    @Test
    void メンバー以外はメンバー以外の承認の方法を使う() {
        given(row(10, 0, "auto", "manual"), null);
        assertThat(applyAndCaptureStatus()).isEqualTo("pending");
    }

    /**
     * 【何のため】メンバー以外を受け付けないイベントは、403（NOT_MEMBER）で、申込みを調べもしないことを確かめる。
     */
    @Test
    void 受け付けないならメンバー以外は403() {
        given(row(10, 0, "auto", "closed"), null);

        assertApiError(() -> service.apply(USER, COMMUNITY_ID, EVENT_ID), 403, "NOT_MEMBER");
        verify(applicationMapper, never()).insert(anyLong(), anyLong(), anyString());
    }

    /**
     * 【何のため】却下された人は申し込み直せない（APPLICATION_REJECTED）、申し込み済みの人は ALREADY_APPLIED になることを確かめる。
     * 締め切りを過ぎていても、すでに申し込んでいることのエラーを先に返す。
     */
    @Test
    void すでに申し込んでいれば409() {
        given(row(10, 0, "auto", "closed", true, false, false), "member");
        when(applicationMapper.findMyApplication(EVENT_ID, USER))
                .thenReturn(new MyApplication("rejected", null, Instant.EPOCH))
                .thenReturn(new MyApplication("waitlisted", 1, Instant.EPOCH));

        assertApiError(() -> service.apply(USER, COMMUNITY_ID, EVENT_ID), 409, "APPLICATION_REJECTED");
        assertApiError(() -> service.apply(USER, COMMUNITY_ID, EVENT_ID), 409, "ALREADY_APPLIED");
    }

    /**
     * 【何のため】締め切りを過ぎたら、申し込めない（APPLICATION_DEADLINE_PASSED）ことを確かめる。
     */
    @Test
    void 締め切りを過ぎたら申し込めない() {
        given(row(10, 0, "auto", "closed", true, false, false), "member");

        assertApiError(() -> service.apply(USER, COMMUNITY_ID, EVENT_ID), 409, "APPLICATION_DEADLINE_PASSED");
        verify(applicationMapper, never()).insert(anyLong(), anyLong(), anyString());
    }

    /**
     * 【何のため】同時に申し込んで、データベースの「1人1回」の制約で止まったときも、ALREADY_APPLIED にすることを確かめる。
     * doThrow(…).when(偽物).メソッド(…) は「このメソッドが呼ばれたら、この例外を投げる」と教える書き方。
     */
    @Test
    void データベースの重複の制約で止まっても409() {
        given(row(10, 0, "auto", "closed"), "member");
        doThrow(new DuplicateKeyException("applications_one_per_user"))
                .when(applicationMapper).insert(EVENT_ID, USER, "applied");

        assertApiError(() -> service.apply(USER, COMMUNITY_ID, EVENT_ID), 409, "ALREADY_APPLIED");
    }

    // ---------- 取り消し ----------

    /**
     * 【何のため】終了したイベントの申込みは、取り消せない（EVENT_ENDED）ことを確かめる。
     */
    @Test
    void 終了したイベントは取り消せない() {
        when(eventService.requireEventForUpdate(COMMUNITY_ID, EVENT_ID))
                .thenReturn(row(10, 0, "auto", "closed", true, true, true));

        assertApiError(() -> service.cancel(USER, COMMUNITY_ID, EVENT_ID), 409, "EVENT_ENDED");
        verify(cancelService, never()).cancel(anyLong(), anyLong(), anyLong());
    }

    /**
     * 【何のため】開始した後でも、終了する前なら取り消せること、自分で取り消したときは「操作した人＝本人」になることを確かめる。
     */
    @Test
    void 開始した後でも終了前なら取り消せる() {
        when(eventService.requireEventForUpdate(COMMUNITY_ID, EVENT_ID))
                .thenReturn(row(10, 0, "auto", "closed", true, true, false));
        when(cancelService.cancel(EVENT_ID, USER, USER)).thenReturn(true);

        service.cancel(USER, COMMUNITY_ID, EVENT_ID);

        verify(cancelService).cancel(EVENT_ID, USER, USER);
    }

    /**
     * 【何のため】申し込んでいなければ（取り消すものがなければ）、NOT_APPLIED になることを確かめる。
     */
    @Test
    void 申し込んでいなければ取り消せない() {
        when(eventService.requireEventForUpdate(COMMUNITY_ID, EVENT_ID)).thenReturn(row(10, 0, "auto", "closed"));

        assertApiError(() -> service.cancel(USER, COMMUNITY_ID, EVENT_ID), 409, "NOT_APPLIED");
    }

    // ---------- 承認・却下 ----------

    /** 【何のため】承認・却下する人（オーナー）とイベントを、偽物に教える。 */
    private void givenManager(EventRow event) {
        Community community = new Community(COMMUNITY_ID, "c", "", MANAGER, "オーナー", 1, null, Instant.EPOCH);
        when(eventService.requireEventForUpdate(COMMUNITY_ID, EVENT_ID)).thenReturn(event);
        when(guard.requireCommunity(COMMUNITY_ID)).thenReturn(community);
        when(guard.requireManager(community, MANAGER)).thenReturn("owner");
    }

    /**
     * 【何のため】承認なら approve、却下なら reject を呼び、決めた後の申込みを返すことを確かめる。
     */
    @Test
    void 承認と却下を呼び分ける() {
        givenManager(row(10, 0, "manual", "closed"));
        when(applicationMapper.findApplication(EVENT_ID, USER))
                .thenReturn(new ApplicationRow(USER, "たろう", "pending", null, Instant.EPOCH))
                .thenReturn(new ApplicationRow(USER, "たろう", "applied", null, Instant.EPOCH));
        when(approvalService.approve(COMMUNITY_ID, EVENT_ID, USER)).thenReturn(true);

        ApplicationResponse response = service.decide(MANAGER, COMMUNITY_ID, EVENT_ID, USER, "approved");

        assertThat(response.status()).isEqualTo("applied");
        verify(approvalService, never()).reject(anyLong(), anyLong());
    }

    /**
     * 【何のため】すでに承認・却下されていれば（approve や reject が false）、APPLICATION_ALREADY_DECIDED になることを確かめる。
     */
    @Test
    void すでに決めた申込みは409() {
        givenManager(row(10, 0, "manual", "closed"));
        when(applicationMapper.findApplication(EVENT_ID, USER))
                .thenReturn(new ApplicationRow(USER, "たろう", "applied", null, Instant.EPOCH));

        assertApiError(() -> service.decide(MANAGER, COMMUNITY_ID, EVENT_ID, USER, "rejected"), 409,
                "APPLICATION_ALREADY_DECIDED");
        verify(approvalService).reject(EVENT_ID, USER);
    }

    /**
     * 【何のため】申込みがなければ 404、イベントが開始していれば EVENT_STARTED で、どちらも承認・却下しないことを確かめる。
     */
    @Test
    void 申込みがないか開始していれば決められない() {
        givenManager(row(10, 0, "manual", "closed", true, true, false));

        assertApiError(() -> service.decide(MANAGER, COMMUNITY_ID, EVENT_ID, USER, "approved"), 404, "NOT_FOUND");

        when(applicationMapper.findApplication(EVENT_ID, USER))
                .thenReturn(new ApplicationRow(USER, "たろう", "pending", null, Instant.EPOCH));
        assertApiError(() -> service.decide(MANAGER, COMMUNITY_ID, EVENT_ID, USER, "approved"), 409, "EVENT_STARTED");
        verify(approvalService, never()).approve(anyLong(), anyLong(), anyLong());
    }

    /**
     * 【何のため】オーナーでも運営スタッフでもなければ、申込みを調べる前に 403 になることを確かめる。
     */
    @Test
    void 管理できない人は承認できない() {
        Community community = new Community(COMMUNITY_ID, "c", "", MANAGER, "オーナー", 1, null, Instant.EPOCH);
        when(eventService.requireEventForUpdate(COMMUNITY_ID, EVENT_ID)).thenReturn(row(10, 0, "manual", "closed"));
        when(guard.requireCommunity(COMMUNITY_ID)).thenReturn(community);
        when(guard.requireManager(community, USER)).thenThrow(ApiException.forbidden());

        assertApiError(() -> service.decide(USER, COMMUNITY_ID, EVENT_ID, USER, "approved"), 403, "FORBIDDEN");
        verify(applicationMapper, never()).findApplication(anyLong(), anyLong());
    }

    /**
     * 【何のため】申込者の一覧で、2ページ目なら31件目から30件を取り出すことを確かめる。
     */
    @Test
    void 申込者の一覧はページで取り出す位置を変える() {
        Community community = new Community(COMMUNITY_ID, "c", "", MANAGER, "オーナー", 1, null, Instant.EPOCH);
        when(guard.requireCommunity(COMMUNITY_ID)).thenReturn(community);
        when(applicationMapper.countApplications(EVENT_ID)).thenReturn(31L);

        var page = service.listApplications(MANAGER, COMMUNITY_ID, EVENT_ID, "2");

        verify(applicationMapper).findApplications(EVENT_ID, 30, 30L);
        assertThat(page.totalPages()).isEqualTo(2);
    }
}

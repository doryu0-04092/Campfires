package com.campfires.event;

import static com.campfires.ApiExceptionAssertions.assertApiError;
import static com.campfires.event.EventRowFixtures.COMMUNITY_ID;
import static com.campfires.event.EventRowFixtures.EVENT_ID;
import static com.campfires.event.EventRowFixtures.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.campfires.common.web.ApiException;
import com.campfires.community.Community;
import com.campfires.community.CommunityGuard;
import com.campfires.notification.NotificationMapper;

/**
 * 【役割】イベントの一覧・検索・作成・編集・削除の業務の処理（EventService）の単体テスト。
 * 【なぜ必要か】編集のときの「定員を申込み済みより少なくできない」「定員を増やしたら繰り上げる」
 * 「承認の方法を自動に変えたら、承認待ちをまとめて承認する（繰り上げの後に）」などの分かれ道と順番を、
 * データベースなしで、すべての組み合わせで確かめるため（要件定義書 5.2、5.8、5.12）。
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final long MANAGER = 60;
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

    @Mock
    private EventMapper eventMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private CommunityGuard guard;
    @Mock
    private EventInputValidator inputValidator;
    @Mock
    private ApplicationApprovalService approvalService;

    private EventService service;

    // 編集で送られてくる入力（中身は、偽物の inputValidator が決めるので、何でもよい）
    private final EventInput input = new EventInput("t", "", null, null, null, "場所", 10, null, null);

    @BeforeEach
    void setUp() {
        service = new EventService(eventMapper, applicationMapper, notificationMapper, guard, inputValidator,
                approvalService);
    }

    private static Community community(boolean archived) {
        return new Community(COMMUNITY_ID, "c", "", MANAGER, "オーナー", 1, archived ? Instant.EPOCH : null,
                Instant.EPOCH);
    }

    /** 【何のため】チェックに通った入力を作る（定員と承認の方法だけを指定する）。 */
    private static ValidEventInput valid(int capacity, String memberApproval, String nonMemberApproval) {
        return new ValidEventInput("t", "", NOW.plusSeconds(86400 * 2), NOW.plusSeconds(86400 * 2 + 7200),
                NOW.plusSeconds(86400), "場所", capacity, memberApproval, nonMemberApproval);
    }

    /** 【何のため】編集の前のイベントと、チェックに通った後の入力を、偽物に教える。 */
    private void givenUpdate(EventRow before, ValidEventInput after) {
        when(eventMapper.lock(COMMUNITY_ID, EVENT_ID)).thenReturn(EVENT_ID);
        when(eventMapper.find(COMMUNITY_ID, EVENT_ID)).thenReturn(before);
        when(guard.requireCommunity(COMMUNITY_ID)).thenReturn(community(false));
        when(guard.requireManager(community(false), MANAGER)).thenReturn("owner");
        when(eventMapper.now()).thenReturn(NOW);
        when(inputValidator.validate(input, NOW, before)).thenReturn(after);
    }

    // ---------- 検索 ----------

    /**
     * 【何のため】検索で300件目より後ろのページを開いたときは、件数だけ数えて、イベントは取り出さないことを確かめる。
     */
    @Test
    void 検索の上限より後ろのページはSQLで取り出さない() {
        when(eventMapper.countSearch(List.of("java"))).thenReturn(500L);

        var response = service.search("Java", "11");

        verify(eventMapper, never()).search(any(), anyInt(), anyLong());
        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isEqualTo(300);
        assertThat(response.limitExceeded()).isTrue();
    }

    /**
     * 【何のため】検索していないときは、1ページ目の30件を取り出し、上限を超えたことにはしないことを確かめる。
     */
    @Test
    void 検索していなければ件数の上限はない() {
        when(eventMapper.countSearch(List.of())).thenReturn(500L);

        var response = service.search("  ", null);

        verify(eventMapper).search(List.of(), 30, 0L);
        assertThat(response.totalCount()).isEqualTo(500);
        assertThat(response.limitExceeded()).isFalse();
    }

    /**
     * 【何のため】コミュニティのイベントの一覧で、status が past のときだけ終了済みを返すことを確かめる（それ以外の値は開催前）。
     */
    @Test
    void pastのときだけ終了済みを返す() {
        service.findCommunityEvents(COMMUNITY_ID, "past", null);
        service.findCommunityEvents(COMMUNITY_ID, "PAST", null);

        verify(eventMapper).countCommunityEvents(COMMUNITY_ID, true);
        verify(eventMapper).countCommunityEvents(COMMUNITY_ID, false);
    }

    // ---------- 作成 ----------

    /**
     * 【何のため】アーカイブ中のコミュニティには作れず（COMMUNITY_ARCHIVED）、入力のチェックもしないことを確かめる。
     */
    @Test
    void アーカイブ中のコミュニティには作れない() {
        when(guard.requireCommunityForUpdate(COMMUNITY_ID)).thenReturn(community(true));

        assertApiError(() -> service.create(MANAGER, COMMUNITY_ID, input), 409, "COMMUNITY_ARCHIVED");
        verify(inputValidator, never()).validate(any(), any(), any());
    }

    /**
     * 【何のため】管理できない人は、アーカイブ中かどうかより先に 403 になることを確かめる（コミュニティの状態を教えない）。
     */
    @Test
    void 管理できない人はアーカイブ中でも403() {
        when(guard.requireCommunityForUpdate(COMMUNITY_ID)).thenReturn(community(true));
        when(guard.requireManager(community(true), 1)).thenThrow(ApiException.forbidden());

        assertApiError(() -> service.create(1, COMMUNITY_ID, input), 403, "FORBIDDEN");
    }

    // ---------- 編集 ----------

    /**
     * 【何のため】イベントがない（ロックできない）ときは 404 で、イベントを読みもしないことを確かめる。
     */
    @Test
    void ロックできなければ404() {
        // Mockito は、教えていない Long の答えを 0 にするので、「行がない」＝ null を、はっきり教える
        when(eventMapper.lock(COMMUNITY_ID, EVENT_ID)).thenReturn(null);

        assertApiError(() -> service.update(MANAGER, COMMUNITY_ID, EVENT_ID, input), 404, "NOT_FOUND");
        verify(eventMapper, never()).find(anyLong(), anyLong());
    }

    /**
     * 【何のため】終了したイベントは編集できない（EVENT_ENDED）ことを確かめる。
     */
    @Test
    void 終了したイベントは編集できない() {
        when(eventMapper.lock(COMMUNITY_ID, EVENT_ID)).thenReturn(EVENT_ID);
        when(eventMapper.find(COMMUNITY_ID, EVENT_ID)).thenReturn(row(10, 0, "auto", "closed", true, true, true));
        when(guard.requireCommunity(COMMUNITY_ID)).thenReturn(community(false));

        assertApiError(() -> service.update(MANAGER, COMMUNITY_ID, EVENT_ID, input), 409, "EVENT_ENDED");
        verify(eventMapper, never()).update(anyLong(), any());
    }

    /**
     * 【何のため】定員を、申込み済みの人数より1人でも少なくできない（CAPACITY_BELOW_APPLIED）ことを確かめる。
     */
    @Test
    void 定員を申込み済みより少なくできない() {
        givenUpdate(row(10, 8, "auto", "closed"), valid(7, "auto", "closed"));

        assertApiError(() -> service.update(MANAGER, COMMUNITY_ID, EVENT_ID, input), 409, "CAPACITY_BELOW_APPLIED");
        verify(eventMapper, never()).update(anyLong(), any());
    }

    /**
     * 【何のため】定員を、申込み済みの人数ちょうどにはできること、保存した後に繰り上げを呼ぶことを確かめる。
     */
    @Test
    void 定員を申込み済みと同じにはできる() {
        ValidEventInput after = valid(8, "auto", "closed");
        givenUpdate(row(10, 8, "auto", "closed"), after);

        service.update(MANAGER, COMMUNITY_ID, EVENT_ID, input);

        InOrder order = inOrder(eventMapper, applicationMapper);
        order.verify(eventMapper).update(EVENT_ID, after);
        order.verify(applicationMapper).promoteWaitlist(EVENT_ID);
        verify(approvalService, never()).approveAllPending(anyLong(), anyLong(), anyBoolean());
    }

    /**
     * 【何のため】承認の方法が「自動以外 → 自動」に変わったときだけ、承認待ちをまとめて承認することを確かめる。
     * @CsvSource の各行は「前のメンバーの方法, 後のメンバーの方法, 前のメンバー以外の方法, 後のメンバー以外の方法,
     * メンバーをまとめて承認するか, メンバー以外をまとめて承認するか」。
     */
    @ParameterizedTest
    @CsvSource({
            "manual, auto,   closed, closed, true,  false",
            "auto,   auto,   manual, auto,   false, true",
            "manual, auto,   closed, auto,   true,  true",
            "auto,   manual, auto,   manual, false, false",
            "auto,   auto,   auto,   auto,   false, false",
            "manual, manual, closed, manual, false, false" })
    void 自動に変えたときだけまとめて承認する(String memberBefore, String memberAfter, String nonMemberBefore,
            String nonMemberAfter, boolean approveMembers, boolean approveNonMembers) {
        givenUpdate(row(10, 0, memberBefore, nonMemberBefore), valid(10, memberAfter, nonMemberAfter));

        service.update(MANAGER, COMMUNITY_ID, EVENT_ID, input);

        // times(回数) で、呼ばれた回数（0回か1回）を確かめる
        verify(approvalService, times(approveMembers ? 1 : 0))
                .approveAllPending(COMMUNITY_ID, EVENT_ID, true);
        verify(approvalService, times(approveNonMembers ? 1 : 0))
                .approveAllPending(COMMUNITY_ID, EVENT_ID, false);
    }

    /**
     * 【何のため】まとめて承認するのは、繰り上げの後であることを確かめる（承認した人が、キャンセル待ちの後ろに並ぶように）。
     */
    @Test
    void まとめて承認するのは繰り上げの後() {
        givenUpdate(row(10, 0, "manual", "closed"), valid(10, "auto", "closed"));

        service.update(MANAGER, COMMUNITY_ID, EVENT_ID, input);

        InOrder order = inOrder(applicationMapper, approvalService);
        order.verify(applicationMapper).promoteWaitlist(EVENT_ID);
        order.verify(approvalService).approveAllPending(COMMUNITY_ID, EVENT_ID, true);
    }

    // ---------- 削除 ----------

    /**
     * 【何のため】削除の前にお知らせを作ることを確かめる（先に削除すると、申込みが消えて、誰に送るか分からなくなる）。
     */
    @Test
    void 削除の前にお知らせを作る() {
        when(eventMapper.lock(COMMUNITY_ID, EVENT_ID)).thenReturn(EVENT_ID);
        when(eventMapper.find(COMMUNITY_ID, EVENT_ID)).thenReturn(row(10, 0, "auto", "closed"));
        when(guard.requireCommunity(COMMUNITY_ID)).thenReturn(community(false));

        service.delete(MANAGER, COMMUNITY_ID, EVENT_ID);

        InOrder order = inOrder(notificationMapper, eventMapper);
        order.verify(notificationMapper).insertEventDeleted(EVENT_ID, MANAGER);
        order.verify(eventMapper).delete(EVENT_ID);
    }

    /**
     * 【何のため】終了したイベントは削除できず（EVENT_ENDED）、お知らせも作らないことを確かめる。
     */
    @Test
    void 終了したイベントは削除できない() {
        when(eventMapper.lock(COMMUNITY_ID, EVENT_ID)).thenReturn(EVENT_ID);
        when(eventMapper.find(COMMUNITY_ID, EVENT_ID)).thenReturn(row(10, 0, "auto", "closed", true, true, true));
        when(guard.requireCommunity(COMMUNITY_ID)).thenReturn(community(false));

        assertApiError(() -> service.delete(MANAGER, COMMUNITY_ID, EVENT_ID), 409, "EVENT_ENDED");
        verify(notificationMapper, never()).insertEventDeleted(anyLong(), anyLong());
        verify(eventMapper, never()).delete(anyLong());
    }

    // ---------- 詳細 ----------

    /**
     * 【何のため】ログインしていない人が詳細を見るときは、立場や申込みの状況を調べないことを確かめる。
     */
    @Test
    void ログインしていなければ自分との関係を調べない() {
        when(eventMapper.find(COMMUNITY_ID, EVENT_ID)).thenReturn(row(10, 4, "auto", "closed"));

        EventDetail detail = service.get(COMMUNITY_ID, EVENT_ID, null);

        assertThat(detail.remainingSeats()).isEqualTo(6);
        assertThat(detail.myRole()).isNull();
        verify(guard, never()).roleOf(anyLong(), anyLong());
        verify(applicationMapper, never()).findMyApplication(anyLong(), anyLong());
    }
}

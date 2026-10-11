package com.campfires.event;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campfires.common.search.SearchQuery;
import com.campfires.common.web.ApiException;
import com.campfires.common.web.PageResponse;
import com.campfires.common.web.Paging;
import com.campfires.common.web.SearchPageResponse;
import com.campfires.community.Community;
import com.campfires.community.CommunityGuard;
import com.campfires.notification.NotificationMapper;

/**
 * 【役割】イベントの一覧・検索・作成・編集・削除の、業務の処理をするクラス。
 * 【なぜ必要か】要件定義書 5.2（イベント）、5.8（一覧と検索）、5.9（アーカイブ）のルールと、権限のチェックを、1か所にまとめるため。
 */
@Service
public class EventService {

    private final EventMapper eventMapper;
    private final ApplicationMapper applicationMapper;
    private final NotificationMapper notificationMapper;
    private final CommunityGuard guard;
    private final EventInputValidator inputValidator;

    public EventService(EventMapper eventMapper, ApplicationMapper applicationMapper,
            NotificationMapper notificationMapper, CommunityGuard guard, EventInputValidator inputValidator) {
        this.eventMapper = eventMapper;
        this.applicationMapper = applicationMapper;
        this.notificationMapper = notificationMapper;
        this.guard = guard;
        this.inputValidator = inputValidator;
    }

    /**
     * 【何のため】開催前のイベントの一覧・検索（要件定義書 5.8）。
     * 検索したときは、当てはまった単語の数が多い順、300件まで。
     */
    public SearchPageResponse<EventSummary> search(String q, String page) {
        SearchQuery query = SearchQuery.of(q, page);
        long matched = eventMapper.countSearch(query.words());
        List<EventSummary> items = query.limit() > 0
                ? eventMapper.search(query.words(), query.limit(), query.offset())
                : List.of();
        return SearchPageResponse.of(items, query.totalCount(matched), query.page(), query.limitExceeded(matched));
    }

    /**
     * 【何のため】自分が申し込んだ、開催前のイベントを、全件返す（要件定義書 5.8）。
     */
    public MyEventListResponse findMyEvents(long userId) {
        return new MyEventListResponse(eventMapper.findMyEvents(userId).stream().map(MyEventRow::toResponse).toList());
    }

    /**
     * 【何のため】コミュニティのイベントの一覧。status が past なら終了済み、それ以外は開催前。
     */
    public PageResponse<EventSummary> findCommunityEvents(long communityId, String status, String pageValue) {
        guard.requireCommunity(communityId);
        boolean past = "past".equals(status);
        int page = Paging.parsePage(pageValue);
        long total = eventMapper.countCommunityEvents(communityId, past);
        return PageResponse.of(eventMapper.findCommunityEvents(communityId, past, Paging.PAGE_SIZE, Paging.offset(page)),
                total, page);
    }

    /**
     * 【何のため】イベントの詳細を返す。ログインしていれば、自分との関係も入れる。
     * @param viewerId 見ている人の番号。ログインしていなければ null
     */
    public EventDetail get(long communityId, long eventId, Long viewerId) {
        return detail(requireEvent(communityId, eventId), viewerId);
    }

    /**
     * 【何のため】イベントを作る。オーナーと運営スタッフだけ。アーカイブ中のコミュニティには作れない（要件定義書 5.9）。
     */
    @Transactional
    public EventDetail create(long userId, long communityId, EventInput input) {
        // コミュニティの行をロックして、アーカイブと同時に作られないようにする
        Community community = guard.requireCommunityForUpdate(communityId);
        guard.requireManager(community, userId);
        if (community.isArchived()) {
            throw ApiException.conflict("COMMUNITY_ARCHIVED", "アーカイブ中のコミュニティでは、イベントを作れません。");
        }
        ValidEventInput valid = inputValidator.validate(input, eventMapper.now());
        long eventId = eventMapper.insert(communityId, valid);
        return detail(requireEvent(communityId, eventId), userId);
    }

    /**
     * 【何のため】イベントを編集する。オーナーと運営スタッフだけ。アーカイブ中でも編集できる（要件定義書 5.9）。
     * 定員を、今の申込み済みの人数より少なくできない。締め切り前に定員を増やしたら、キャンセル待ちの人を繰り上げる。
     */
    @Transactional
    public EventDetail update(long userId, long communityId, long eventId, EventInput input) {
        // イベントの行をロックして、編集と申込みが同時に起きても、定員の確認がずれないようにする
        EventRow event = requireEventForUpdate(communityId, eventId);
        guard.requireManager(guard.requireCommunity(communityId), userId);
        if (event.ended()) {
            throw ApiException.conflict("EVENT_ENDED", "終了したイベントは編集できません。");
        }
        ValidEventInput valid = inputValidator.validate(input, eventMapper.now());
        if (valid.capacity() < event.appliedCount()) {
            throw ApiException.conflict("CAPACITY_BELOW_APPLIED",
                    "定員を、今の申込み済みの人数（" + event.appliedCount() + "人）より少なくできません。");
        }
        eventMapper.update(eventId, valid);
        // 定員が増えた分だけ、キャンセル待ちの人を繰り上げる（締め切り後は繰り上げない。SQL の中で確かめる）
        applicationMapper.promoteWaitlist(eventId);
        return detail(requireEvent(communityId, eventId), userId);
    }

    /**
     * 【何のため】イベントを削除する。オーナーと運営スタッフだけ。終了したイベントは、参加の記録として残すため削除できない。
     * 申し込んでいた人（キャンセル待ちを含む）に、お知らせを送る（要件定義書 5.10）。
     */
    @Transactional
    public void delete(long userId, long communityId, long eventId) {
        EventRow event = requireEventForUpdate(communityId, eventId);
        guard.requireManager(guard.requireCommunity(communityId), userId);
        if (event.ended()) {
            throw ApiException.conflict("EVENT_ENDED", "終了したイベントは、参加の記録として残すため削除できません。");
        }
        // 削除する前に、お知らせを作る（削除すると、申込みも消えて、誰に送るか分からなくなるため）
        notificationMapper.insertEventDeleted(eventId, userId);
        eventMapper.delete(eventId);
    }

    /**
     * 【何のため】イベントを探す。見つからなければ 404。ほかの機能（申込み）からも使う。
     */
    EventRow requireEvent(long communityId, long eventId) {
        EventRow event = eventMapper.find(communityId, eventId);
        if (event == null) {
            throw notFound();
        }
        return event;
    }

    /**
     * 【何のため】イベントを探し、行をロックする。見つからなければ 404。
     */
    EventRow requireEventForUpdate(long communityId, long eventId) {
        if (eventMapper.lock(communityId, eventId) == null) {
            throw notFound();
        }
        // ロックを取った後に、別の SQL で読み直す（ほかの人の申込みが終わった後の、新しい人数を読むため）
        return requireEvent(communityId, eventId);
    }

    /**
     * 【何のため】イベントの詳細の返事を作る。見ている人との関係（立場、申込みの状況）も入れる。
     */
    private EventDetail detail(EventRow event, Long viewerId) {
        String myRole = null;
        MyApplication myApplication = null;
        if (viewerId != null) {
            myRole = guard.roleOf(event.communityId(), viewerId);
            myApplication = applicationMapper.findMyApplication(event.id(), viewerId);
        }
        return EventDetail.of(event, myRole, myApplication);
    }

    private static ApiException notFound() {
        return ApiException.notFound("イベントが見つかりません。");
    }
}

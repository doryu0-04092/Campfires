package com.campfires.community;

import static com.campfires.ApiExceptionAssertions.assertApiError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 【役割】コミュニティの存在と権限を確かめる CommunityGuard の単体テスト。
 * 【なぜ必要か】権限のチェック（要件定義書 3章の表）は、多くの API の入口で使われる。
 * 「オーナー・運営スタッフは通す、メンバー・メンバー以外は 403」「コミュニティがなければ 404」を、
 * すべての立場の組み合わせで確かめるため。
 *
 * Mockito は「偽物の部品（モック）」を作る道具。Mapper の偽物を使い、データベースなしで、決めた答えを返させる。
 * @ExtendWith(MockitoExtension.class) は、@Mock を付けた項目に、偽物を自動で入れるための印。
 */
@ExtendWith(MockitoExtension.class)
// LENIENT（ゆるい）：教えていない値での呼び出し（メンバーでない人の立場を調べる、など）も、null を返すだけにする。
// ふだんの設定（STRICT_STUBS）では、教えた値と違う値で呼ぶと、まちがいとして止まってしまうため
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunityGuardTest {

    // オーナーのユーザーの番号と、コミュニティの番号
    static final long OWNER = 100;
    static final long COMMUNITY_ID = 1;

    @Mock
    private CommunityMapper communityMapper;
    @Mock
    private MemberMapper memberMapper;

    private CommunityGuard guard;

    @BeforeEach
    void setUp() {
        guard = new CommunityGuard(communityMapper, memberMapper);
    }

    /** 【何のため】テストで使うコミュニティを作る。archived が true ならアーカイブ中。 */
    static Community community(boolean archived) {
        return new Community(COMMUNITY_ID, "テストのコミュニティ", "", OWNER, "オーナー", 1,
                archived ? Instant.parse("2026-10-01T00:00:00Z") : null, Instant.parse("2026-09-01T00:00:00Z"));
    }

    /**
     * 【何のため】コミュニティがなければ、ロックありでもなしでも 404 になることを確かめる。
     */
    @Test
    void コミュニティがなければ404() {
        // when(…).thenReturn(…) は「この呼び出しには、この答えを返す」と偽物に教える書き方。教えていなければ null を返す
        assertApiError(() -> guard.requireCommunity(COMMUNITY_ID), 404, "NOT_FOUND");
        assertApiError(() -> guard.requireCommunityForUpdate(COMMUNITY_ID), 404, "NOT_FOUND");
    }

    /**
     * 【何のため】コミュニティがあれば、そのまま返すことを確かめる（ロックありは、ロックする SQL を使う）。
     */
    @Test
    void コミュニティがあれば返す() {
        when(communityMapper.findById(COMMUNITY_ID)).thenReturn(community(false));
        when(communityMapper.findByIdForUpdate(COMMUNITY_ID)).thenReturn(community(true));

        assertThat(guard.requireCommunity(COMMUNITY_ID).isArchived()).isFalse();
        assertThat(guard.requireCommunityForUpdate(COMMUNITY_ID).isArchived()).isTrue();
    }

    /**
     * 【何のため】オーナーと運営スタッフは通して立場を返し、メンバーとメンバー以外は 403 にすることを確かめる。
     */
    @Test
    void 管理できるのはオーナーと運営スタッフだけ() {
        when(memberMapper.findRole(COMMUNITY_ID, 1)).thenReturn("owner");
        when(memberMapper.findRole(COMMUNITY_ID, 2)).thenReturn("staff");
        when(memberMapper.findRole(COMMUNITY_ID, 3)).thenReturn("member");

        assertThat(guard.requireManager(community(false), 1)).isEqualTo("owner");
        assertThat(guard.requireManager(community(false), 2)).isEqualTo("staff");
        assertApiError(() -> guard.requireManager(community(false), 3), 403, "FORBIDDEN");
        assertApiError(() -> guard.requireManager(community(false), 4), 403, "FORBIDDEN");
    }

    /**
     * 【何のため】オーナーかどうかは、コミュニティのオーナーの番号で決まることを確かめる（運営スタッフは 403）。
     */
    @Test
    void オーナーだけの操作はオーナー以外は403() {
        guard.requireOwner(community(false), OWNER);

        assertApiError(() -> guard.requireOwner(community(false), OWNER + 1), 403, "FORBIDDEN");
    }

    /**
     * 【何のため】メンバーでなければ、立場が null になることを確かめる。
     */
    @Test
    void メンバーでなければ立場はnull() {
        assertThat(guard.roleOf(COMMUNITY_ID, 999)).isNull();
    }
}

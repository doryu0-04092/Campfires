-- 【役割】Campfires のすべてのテーブルを作る、最初のマイグレーション（テーブルの変更の記録）。
-- 【なぜ必要か】テーブルの形を SQL のファイルとして残し、誰の PC でも、本番でも、同じテーブルを作れるようにするため。
-- Flyway が、アプリの起動時に、まだ実行していないファイル（V1、V2…）を番号の順に実行する。
-- 一度 main に入ったファイルは書き換えない。テーブルを変えるときは、新しい番号のファイル（V2__…）を足す。
--
-- 日時は、すべて timestamptz（タイムゾーン付きの日時）で、世界標準時として保存する（非機能要件 6章）。
-- 番号（id）は、データベースが自動で付ける（GENERATED ALWAYS AS IDENTITY）。

-- ============================================================
-- ユーザー（要件定義書 5.5、5.6）
-- ============================================================
CREATE TABLE users (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 本名、ニックネーム、メールアドレス、パスワード（BCrypt で変換した値）。
    -- アカウントを削除したときは、行は残したまま、この4つを NULL（空）にする
    real_name           VARCHAR(50),
    nickname            VARCHAR(30),
    email               VARCHAR(254),
    password_hash       VARCHAR(100),
    -- プライバシーポリシーと利用規約に同意した日時と、そのときの規約のバージョン（法的な論点のメモ）
    policies_agreed_at  TIMESTAMPTZ  NOT NULL,
    policies_version    VARCHAR(20)  NOT NULL,
    -- アカウントを削除した日時。削除していなければ NULL
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 削除していないユーザーは、4つの項目が必ず入っている
    CONSTRAINT users_active_has_profile CHECK (
        deleted_at IS NOT NULL
        OR (real_name IS NOT NULL AND nickname IS NOT NULL AND email IS NOT NULL AND password_hash IS NOT NULL)
    )
);

-- メールアドレスは、削除していないユーザーの中で重複させない（大文字と小文字は区別しない）。
-- WHERE を付けた索引（部分インデックス）にして、削除したユーザーと同じメールアドレスで、もう一度登録できるようにする
CREATE UNIQUE INDEX users_email_unique ON users (lower(email)) WHERE deleted_at IS NULL;

-- ============================================================
-- ログインの失敗の記録（ログインの試行制限。非機能要件 3章）
-- ============================================================
CREATE TABLE login_failures (
    -- 小文字にそろえたメールアドレス（登録されていないメールアドレスの失敗も数える）
    email          VARCHAR(254) PRIMARY KEY,
    -- 続けて失敗した回数
    failure_count  INTEGER      NOT NULL,
    -- ロックが解ける日時。ロックされていなければ NULL
    locked_until   TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================
-- コミュニティ（要件定義書 5.1、5.9）
-- ============================================================
CREATE TABLE communities (
    id           BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(50)   NOT NULL,
    description  VARCHAR(2000) NOT NULL DEFAULT '',
    -- オーナーのユーザー
    owner_id     BIGINT        NOT NULL REFERENCES users (id),
    -- アーカイブした日時。アーカイブしていなければ NULL
    archived_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ============================================================
-- メンバー（どのユーザーが、どのコミュニティに、どの立場でいるか）
-- ============================================================
CREATE TABLE memberships (
    community_id  BIGINT       NOT NULL REFERENCES communities (id),
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    -- owner：オーナー、staff：運営スタッフ、member：メンバー
    role          VARCHAR(10)  NOT NULL CHECK (role IN ('owner', 'staff', 'member')),
    joined_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 同じコミュニティに、同じユーザーは1つの立場でだけ入る
    PRIMARY KEY (community_id, user_id)
);

-- 自分が参加しているコミュニティを探すための索引
CREATE INDEX memberships_user ON memberships (user_id);

-- ============================================================
-- 参加申請（要件定義書 5.1）
-- ============================================================
CREATE TABLE join_requests (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    community_id  BIGINT       NOT NULL REFERENCES communities (id),
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    -- pending：申請中、approved：承認済み、rejected：却下
    status        VARCHAR(10)  NOT NULL CHECK (status IN ('pending', 'approved', 'rejected')),
    requested_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 承認・却下した日時
    decided_at    TIMESTAMPTZ
);

-- 同じコミュニティに、同じユーザーの「申請中」は1件だけ（二重の申請を、データベースでも防ぐ）
CREATE UNIQUE INDEX join_requests_one_pending ON join_requests (community_id, user_id) WHERE status = 'pending';

-- ============================================================
-- イベント（要件定義書 5.2）
-- ============================================================
CREATE TABLE events (
    id                    BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    community_id          BIGINT        NOT NULL REFERENCES communities (id),
    title                 VARCHAR(100)  NOT NULL,
    description           VARCHAR(5000) NOT NULL DEFAULT '',
    start_at              TIMESTAMPTZ   NOT NULL,
    end_at                TIMESTAMPTZ   NOT NULL,
    -- 申込み締め切り日時（キャンセルの期限も兼ねる）
    application_deadline  TIMESTAMPTZ   NOT NULL,
    location              VARCHAR(200)  NOT NULL,
    capacity              INTEGER       NOT NULL CHECK (capacity BETWEEN 1 AND 1000),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- 日時の前後関係：締め切り < 開始 < 終了
    CONSTRAINT events_time_order CHECK (application_deadline < start_at AND start_at < end_at)
);

CREATE INDEX events_community ON events (community_id);
-- 開催前のイベントを探すための索引
CREATE INDEX events_end_at ON events (end_at);

-- ============================================================
-- 申込み（要件定義書 5.2、5.3）
-- ============================================================
CREATE TABLE applications (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- イベントを削除したら、申込みも一緒に削除する（ON DELETE CASCADE）
    event_id    BIGINT       NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    -- applied：申込み済み、waitlisted：キャンセル待ち
    status      VARCHAR(10)  NOT NULL CHECK (status IN ('applied', 'waitlisted')),
    applied_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 1人が同じイベントに申し込めるのは1回だけ（二重の申込みを、データベースでも防ぐ。非機能要件 2章）
    CONSTRAINT applications_one_per_user UNIQUE (event_id, user_id)
);

-- 申込み済みの人数を数えるとき、キャンセル待ちを順番に並べるときの索引
CREATE INDEX applications_event_status ON applications (event_id, status, applied_at);
CREATE INDEX applications_user ON applications (user_id);

-- ============================================================
-- お知らせ（要件定義書 5.10）
-- ============================================================
CREATE TABLE notifications (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- お知らせを受け取るユーザー
    user_id        BIGINT        NOT NULL REFERENCES users (id),
    -- late_cancellation：締め切り後のキャンセル、event_deleted：イベントの削除
    type           VARCHAR(30)   NOT NULL CHECK (type IN ('late_cancellation', 'event_deleted')),
    -- 出来事が起きた時点のイベントのタイトル（イベントが削除されても、何のイベントか分かるように残す）
    event_title    VARCHAR(100)  NOT NULL,
    community_id   BIGINT        NOT NULL REFERENCES communities (id),
    -- イベントの番号。イベントが削除されたら NULL にする（ON DELETE SET NULL）
    event_id       BIGINT        REFERENCES events (id) ON DELETE SET NULL,
    -- 出来事を起こした人（締め切り後にキャンセルした人）
    actor_user_id  BIGINT        REFERENCES users (id),
    -- 既読にした日時。未読なら NULL
    read_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- 自分あてのお知らせを、新しい順に取り出すための索引
CREATE INDEX notifications_user_created ON notifications (user_id, created_at DESC);
-- 未読の件数を数えるための索引（未読の行だけを入れた、部分インデックス）
CREATE INDEX notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;

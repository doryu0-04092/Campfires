-- 【役割】ログインの状態（セッション）を、データベースに保存するためのテーブルを作るマイグレーション（Issue #47）。
-- 【なぜ必要か】本番では、アプリの箱（コンテナ）を入れ替えたり、2つ以上に増やしたりする。
-- セッションを箱の中（メモリ）に置くと、そのたびにログインが切れるので、どの箱からも読めるデータベースに置く。
-- テーブルの形は、Spring Session JDBC が用意している PostgreSQL 用の定義（schema-postgresql.sql）と同じ。
-- 開発用（npm run dev:api）と自動テストでは、この仕組みを使わない（テーブルがあるだけで、空のまま）。

-- セッション1つ分（だれのセッションか、いつまで有効か）
CREATE TABLE spring_session (
    primary_id             CHAR(36)     NOT NULL,
    session_id             CHAR(36)     NOT NULL,
    creation_time          BIGINT       NOT NULL,
    last_access_time       BIGINT       NOT NULL,
    max_inactive_interval  INT          NOT NULL,
    expiry_time            BIGINT       NOT NULL,
    principal_name         VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
-- 有効期限が切れたセッションを、まとめて消すときに使う索引
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

-- セッションに保存した値（ログインしている人など）。セッションを消すと、一緒に消える（ON DELETE CASCADE）
CREATE TABLE spring_session_attributes (
    session_primary_id  CHAR(36)      NOT NULL,
    attribute_name      VARCHAR(200)  NOT NULL,
    attribute_bytes     BYTEA         NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);

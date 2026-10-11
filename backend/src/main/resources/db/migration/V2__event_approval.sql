-- 【役割】イベントの参加に「承認の方法（自動・手動）」を足すマイグレーション（Issue #43）。
-- 【なぜ必要か】イベントごとに、コミュニティのメンバーとメンバー以外の、参加の承認の方法を選べるようにするため。
-- 一度 main に入った V1 は書き換えず、変更はこのファイルに書く。

-- ============================================================
-- イベント：承認の方法
-- ============================================================
ALTER TABLE events
    -- コミュニティのメンバーの承認の方法。auto：自動、manual：手動
    ADD COLUMN member_approval VARCHAR(10) NOT NULL DEFAULT 'auto'
        CHECK (member_approval IN ('auto', 'manual')),
    -- メンバー以外の承認の方法。auto：自動、manual：手動、closed：受け付けない
    ADD COLUMN non_member_approval VARCHAR(10) NOT NULL DEFAULT 'closed'
        CHECK (non_member_approval IN ('auto', 'manual', 'closed'));

-- ============================================================
-- 申込み：承認待ち・却下の状態と、席の順番
-- ============================================================
-- 状態に pending（承認待ち）と rejected（却下）を足す。
-- V1 で列に付けた CHECK は、PostgreSQL が「表_列_check」という名前を自動で付けている
ALTER TABLE applications DROP CONSTRAINT applications_status_check;
ALTER TABLE applications ADD CONSTRAINT applications_status_check
    CHECK (status IN ('pending', 'applied', 'waitlisted', 'rejected'));

-- 席の順番を決める日時（申込み済み、またはキャンセル待ちになった日時）。
-- 自動なら申し込んだ日時、手動なら承認された日時。承認待ち・却下のときは NULL。
-- キャンセル待ちの順番と繰り上げは、この日時の順にする（承認待ちの間は、順番を取らない）
ALTER TABLE applications ADD COLUMN queued_at TIMESTAMPTZ;
UPDATE applications SET queued_at = applied_at;
ALTER TABLE applications ADD CONSTRAINT applications_queued_at_by_status
    CHECK ((status IN ('applied', 'waitlisted')) = (queued_at IS NOT NULL));

-- 申込み済みの人数を数えるとき、キャンセル待ちを順番に並べるときの索引を、新しい順番の列で作り直す
DROP INDEX applications_event_status;
CREATE INDEX applications_event_status ON applications (event_id, status, queued_at);

-- ============================================================
-- お知らせ：承認・却下の種類
-- ============================================================
-- application_approved：参加が承認された、application_rejected：参加が却下された（申し込んだ人あて）
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('late_cancellation', 'event_deleted', 'application_approved', 'application_rejected'));

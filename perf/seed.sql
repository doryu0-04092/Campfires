-- 【役割】負荷テスト用に、非機能要件（docs/non-functional-requirements.md）の件数のデータを入れる SQL。
-- 【なぜ必要か】「ユーザー 1万人、コミュニティ 1,000件、イベント 1万件で、決めた時間を守る」かを確かめるため。
-- 手元の負荷テスト用のデータベースにだけ使う（本番には使わない）。使い方は perf/README.md。
--
-- :'password_hash' は、psql に -v password_hash=... で渡す値（負荷テスト用のパスワードを BCrypt で変換したもの）。
-- generate_series(1, N) は「1 から N までの数を、1行ずつ作る」PostgreSQL の関数。

-- ---------- ユーザー：1万人 ----------
INSERT INTO users (real_name, nickname, email, password_hash, policies_agreed_at, policies_version)
SELECT 'perf user ' || i, 'perf-' || i, 'perf-' || i || '@example.com', :'password_hash', now(), '2026-10-11'
FROM generate_series(1, 10000) AS i;

-- ---------- コミュニティ：1,000件（オーナーは、ユーザーの 1〜1000番） ----------
-- 検索で当たるように、話題（Java、Python など）を順番に入れる
INSERT INTO communities (name, description, owner_id, created_at)
SELECT
  (ARRAY['Java', 'Python', 'デザイン', 'インフラ', 'データベース', 'React', 'Go', 'セキュリティ', 'AI', 'もくもく会'])[1 + i % 10]
    || ' 勉強会 ' || i,
  'これは負荷テスト用のコミュニティです。' || (ARRAY['初心者歓迎', 'オンライン', '東京', '大阪', '週末'])[1 + i % 5],
  u.id,
  now() - (i || ' hours')::interval
FROM generate_series(1, 1000) AS i
JOIN users u ON u.email = 'perf-' || i || '@example.com';

-- オーナーを、メンバーの一覧に入れる
INSERT INTO memberships (community_id, user_id, role)
SELECT id, owner_id, 'owner' FROM communities;

-- ふつうのメンバー：ユーザーの 1001〜10000番が、それぞれ3つのコミュニティに参加する（2万7千件）
INSERT INTO memberships (community_id, user_id, role)
SELECT DISTINCT c.id, u.id, 'member'
FROM generate_series(1001, 10000) AS i
CROSS JOIN generate_series(0, 2) AS k
JOIN users u ON u.email = 'perf-' || i || '@example.com'
JOIN communities c ON c.name LIKE '% 勉強会 ' || (1 + (i * 7 + k * 331) % 1000)
ON CONFLICT DO NOTHING;

-- ---------- イベント：1万件（各コミュニティに10件。8割は開催前、2割は終了済み） ----------
INSERT INTO events (community_id, title, description, start_at, end_at, application_deadline,
                    location, capacity, member_approval, non_member_approval)
SELECT
  c.id,
  split_part(c.name, ' ', 1) || ' もくもく会 第' || j || '回',
  '負荷テスト用のイベントです。' || c.description,
  -- j が 1〜8 なら、今から 1〜60日後。9、10 なら、過去
  CASE WHEN j <= 8 THEN now() + ((c.id * 13 + j * 7) % 60 + 1 || ' days')::interval
       ELSE now() - ((c.id + j) % 60 + 2 || ' days')::interval END,
  CASE WHEN j <= 8 THEN now() + ((c.id * 13 + j * 7) % 60 + 1 || ' days')::interval + interval '2 hours'
       ELSE now() - ((c.id + j) % 60 + 2 || ' days')::interval + interval '2 hours' END,
  CASE WHEN j <= 8 THEN now() + ((c.id * 13 + j * 7) % 60 || ' days')::interval + interval '1 hour'
       ELSE now() - ((c.id + j) % 60 + 3 || ' days')::interval END,
  'オンライン',
  50,
  'auto',
  'auto'
FROM communities c
CROSS JOIN generate_series(1, 10) AS j;

-- ---------- 申込み：開催前のイベントの一部に、メンバーが申し込んでいる（約2万件） ----------
INSERT INTO applications (event_id, user_id, status, applied_at, queued_at)
SELECT e.id, m.user_id, 'applied', now(), now()
FROM events e
JOIN memberships m ON m.community_id = e.community_id AND m.role = 'member'
WHERE e.start_at > now() AND (e.id + m.user_id) % 4 = 0
ON CONFLICT DO NOTHING;

-- 統計の情報を新しくする（PostgreSQL が、速い探し方を選べるように）
ANALYZE;

SELECT 'users' AS t, count(*) FROM users
UNION ALL SELECT 'communities', count(*) FROM communities
UNION ALL SELECT 'memberships', count(*) FROM memberships
UNION ALL SELECT 'events', count(*) FROM events
UNION ALL SELECT 'applications', count(*) FROM applications;

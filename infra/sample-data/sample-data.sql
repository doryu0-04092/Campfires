-- 【役割】本番（と、手元の確認用の箱）に入れる、見本のデータを作る SQL。
-- 【なぜ必要か】アプリを見てもらうときに、実際に使われているような状態（利用者・コミュニティ・イベント・申込み・お知らせ）で
-- 見られるようにするため。確認用のアカウントで、一通りの機能（オーナーとしての管理と、利用者としての申込み）を確かめられるようにする。
--
-- 注意：はじめに、今あるデータを【すべて消す】（利用者、コミュニティ、イベント、申込み、お知らせ、ログインの状態など）。
-- テーブルの作り（Flyway の記録）は消さない。使い方は infra/sample-data/README.md。
--
-- psql に渡す値（-v 名前=値）：
--   demo_password：確認用のアカウントのパスワード（README に載せる。ほかの利用者のパスワードは、ランダムにして、どこにも残さない）
--
-- 日時は、このSQLを流した時刻からの相対で作る（いつ流しても、開催前のイベントが開催前になるように）。

\set ON_ERROR_STOP on

BEGIN;

-- ---------- 0. 道具（このSQLの中だけで使う。pg_temp は、つないでいるあいだだけの場所） ----------
-- パスワードを、アプリと同じ BCrypt（強さ 10）で変換するために、pgcrypto を使う（最後に消す）
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- メールアドレスの前の部分から、利用者の番号を返す
CREATE FUNCTION pg_temp.u(name text) RETURNS bigint AS $$
  SELECT id FROM users WHERE email = name || '@example.com'
$$ LANGUAGE sql;

-- コミュニティの名前から、番号を返す
CREATE FUNCTION pg_temp.c(name text) RETURNS bigint AS $$
  SELECT id FROM communities WHERE communities.name = c.name
$$ LANGUAGE sql;

-- イベントのタイトルから、番号を返す
CREATE FUNCTION pg_temp.e(title text) RETURNS bigint AS $$
  SELECT id FROM events WHERE events.title = e.title
$$ LANGUAGE sql;

-- 今日から days 日後の、日本時間の hour 時（hour に 19.5 のような小数を渡すと 19時30分）
CREATE FUNCTION pg_temp.jst(days numeric, hour numeric) RETURNS timestamptz AS $$
  SELECT (date_trunc('day', now() AT TIME ZONE 'Asia/Tokyo') + make_interval(days => days::int)
          + (hour || ' hours')::interval) AT TIME ZONE 'Asia/Tokyo'
$$ LANGUAGE sql;

-- イベントを作る。締め切りは、開始の deadline_hours 時間前
CREATE FUNCTION pg_temp.ev(community text, title text, description text, days numeric, hour numeric,
                           hours numeric, deadline_hours numeric, location text, capacity int,
                           member_approval text DEFAULT 'auto', non_member_approval text DEFAULT 'closed')
RETURNS void AS $$
  INSERT INTO events (community_id, title, description, start_at, end_at, application_deadline, location, capacity,
                      member_approval, non_member_approval, created_at)
  VALUES (pg_temp.c(community), title, description, pg_temp.jst(days, hour),
          pg_temp.jst(days, hour) + (hours || ' hours')::interval,
          pg_temp.jst(days, hour) - (deadline_hours || ' hours')::interval,
          location, capacity, member_approval, non_member_approval,
          LEAST(now(), pg_temp.jst(days, hour) - interval '21 days'))
$$ LANGUAGE sql;

-- 申し込む。applied（申込み済み）と waitlisted（キャンセル待ち）は、席の順番（queued_at）を持つ。
-- pending（承認待ち）と rejected（却下）は、席の順番を持たない（テーブルの決まり）
CREATE FUNCTION pg_temp.ap(event_title text, who text, status text, ago_hours numeric) RETURNS void AS $$
  INSERT INTO applications (event_id, user_id, status, applied_at, queued_at)
  VALUES (pg_temp.e(event_title), pg_temp.u(who), status, now() - (ago_hours || ' hours')::interval,
          CASE WHEN status IN ('applied', 'waitlisted') THEN now() - (ago_hours || ' hours')::interval END)
$$ LANGUAGE sql;

-- コミュニティに入れる（立場：owner・staff・member）
CREATE FUNCTION pg_temp.join(community text, who text, role text, ago_days numeric) RETURNS void AS $$
  INSERT INTO memberships (community_id, user_id, role, joined_at)
  VALUES (pg_temp.c(community), pg_temp.u(who), role, now() - (ago_days || ' days')::interval)
$$ LANGUAGE sql;

-- ---------- 1. 今あるデータを、すべて消す ----------
TRUNCATE notifications, applications, events, join_requests, memberships, communities,
         password_reset_tokens, login_failures, spring_session_attributes, spring_session, users
         RESTART IDENTITY CASCADE;

-- ---------- 2. 利用者（30人） ----------
-- オーナー 5人（demo は確認用のアカウント）、運営スタッフ 10人、ふつうのメンバー 15人
INSERT INTO users (real_name, nickname, email, password_hash, policies_agreed_at, policies_version, created_at)
SELECT real_name, nickname, login || '@example.com',
       CASE WHEN login = 'demo' THEN crypt(:'demo_password', gen_salt('bf', 10))
            ELSE crypt(gen_random_uuid()::text, gen_salt('bf', 10)) END,
       now() - (days_ago || ' days')::interval, '2026-10-11', now() - (days_ago || ' days')::interval
FROM (VALUES
  -- オーナー
  ('demo',      '確認 太郎',   'たろう（確認用）', 120),
  ('misaki',    '佐藤 美咲',   'みさき',         110),
  ('kenta',     '鈴木 健太',   'けんた',         105),
  ('aoi',       '高橋 葵',     'あおい',         100),
  ('daisuke',   '田中 大輔',   'だいすけ',        95),
  -- 運営スタッフ
  ('yui',       '伊藤 結衣',   'ゆい',            90),
  ('sho',       '渡辺 翔',     'しょう',          88),
  ('haruka',    '山本 遥',     'はるか',          85),
  ('ryo',       '中村 涼',     'りょう',          80),
  ('nanami',    '小林 七海',   'ななみ',          78),
  ('takumi',    '加藤 拓海',   'たくみ',          75),
  ('mai',       '吉田 舞',     'まい',            72),
  ('yuto',      '山田 悠斗',   'ゆうと',          70),
  ('saki',      '佐々木 咲',   'さき',            66),
  ('kaito',     '山口 海斗',   'かいと',          63),
  -- ふつうのメンバー
  ('rin',       '松本 凛',     'りん',            60),
  ('sota',      '井上 蒼太',   'そうた',          58),
  ('hina',      '木村 陽菜',   'ひな',            55),
  ('ren',       '林 蓮',       'れん',            52),
  ('mio',       '清水 澪',     'みお',            50),
  ('hayato',    '山崎 隼人',   'はやと',          47),
  ('akari',     '森 あかり',   'あかり',          45),
  ('yuki',      '池田 優希',   'ゆうき',          42),
  ('kotone',    '橋本 琴音',   'ことね',          40),
  ('riku',      '阿部 陸',     'りく',            37),
  ('sakura',    '石川 さくら', 'さくら',          35),
  ('kazuki',    '前田 和樹',   'かずき',          30),
  ('emi',       '藤田 恵美',   'えみ',            25),
  ('tomoya',    '後藤 智也',   'ともや',          20),
  ('nozomi',    '岡田 希',     'のぞみ',          14)
) AS t(login, real_name, nickname, days_ago);

-- ---------- 3. コミュニティ（12件。1件はアーカイブ済み） ----------
INSERT INTO communities (name, description, owner_id, archived_at, created_at)
SELECT name, description, pg_temp.u(owner), CASE WHEN archived THEN now() - interval '20 days' END,
       now() - (days_ago || ' days')::interval
FROM (VALUES
  -- 確認用のアカウント（たろう）がオーナー
  ('Java 勉強会 東京', '毎月、Java と Spring Boot を一緒に学ぶ勉強会です。初心者から実務経験者まで歓迎します。ハンズオンと、本の輪読を交互に行っています。', 'demo', false, 118),
  ('週末 React もくもく会', '週末に集まって、それぞれ React のアプリを作る会です。困ったことがあれば、その場で相談できます。', 'demo', false, 100),
  ('AWS 初心者ハンズオン', 'Terraform で AWS の環境を作りながら、クラウドの基本を学びます。毎回、作った環境は最後に片付けます。', 'demo', false, 80),
  ('Python 読書会', '「Python 実践入門」を1章ずつ読む会でした。読み終えたので、いったんお休みしています。', 'demo', true, 200),
  -- ほかのオーナー
  ('デザイン思考ラボ', 'サービスのデザインと、ユーザー調査を学ぶコミュニティです。', 'misaki', false, 108),
  ('朝活 英会話クラブ', '平日の朝 7時から、オンラインで英会話を練習します。', 'misaki', false, 90),
  ('皇居ランニング部', '週末の朝に、皇居の周り（1周 5km）を走ります。ペースは自由です。', 'kenta', false, 103),
  ('データ分析もくもく会', 'pandas や SQL で、公開データを分析して発表し合います。', 'kenta', false, 70),
  ('Go 言語 入門会', 'A Tour of Go から始めて、小さな API を作ります。', 'aoi', false, 98),
  ('アジャイル実践コミュニティ', 'スクラムの実践例を持ち寄って話し合う会です。', 'aoi', false, 60),
  ('写真散歩の会', '季節の風景を撮りに、東京の街を歩きます。カメラはスマートフォンでも大丈夫です。', 'daisuke', false, 93),
  ('セキュリティ勉強会', 'OWASP Top 10 を題材に、Web アプリの守り方を学びます。', 'daisuke', false, 50)
) AS t(name, description, owner, archived, days_ago);

-- オーナーを、メンバーの一覧に入れる
INSERT INTO memberships (community_id, user_id, role, joined_at)
SELECT id, owner_id, 'owner', created_at FROM communities;

-- 運営スタッフ（10人。それぞれ、1つか2つのコミュニティの運営スタッフ）
SELECT pg_temp.join(c, u, 'staff', d) FROM (VALUES
  ('Java 勉強会 東京', 'yui', 100), ('Java 勉強会 東京', 'sho', 95),
  ('週末 React もくもく会', 'haruka', 90),
  ('AWS 初心者ハンズオン', 'ryo', 75), ('AWS 初心者ハンズオン', 'nanami', 70),
  ('デザイン思考ラボ', 'takumi', 100), ('朝活 英会話クラブ', 'mai', 80),
  ('皇居ランニング部', 'yuto', 95), ('Go 言語 入門会', 'saki', 90),
  ('写真散歩の会', 'kaito', 85), ('セキュリティ勉強会', 'yui', 45)
) AS t(c, u, d);

-- 確認用のアカウントは、ほかのオーナーの5つのコミュニティのメンバー
SELECT pg_temp.join(c, 'demo', 'member', d) FROM (VALUES
  ('デザイン思考ラボ', 100), ('朝活 英会話クラブ', 85), ('皇居ランニング部', 90),
  ('Go 言語 入門会', 80), ('写真散歩の会', 70)
) AS t(c, d);

-- ふつうのメンバー（それぞれ、2〜4つのコミュニティのメンバー）
SELECT pg_temp.join(c, u, 'member', d) FROM (VALUES
  ('Java 勉強会 東京', 'rin', 90), ('Java 勉強会 東京', 'sota', 88), ('Java 勉強会 東京', 'hina', 80),
  ('Java 勉強会 東京', 'ren', 70), ('Java 勉強会 東京', 'mio', 65), ('Java 勉強会 東京', 'hayato', 60),
  ('Java 勉強会 東京', 'akari', 55), ('Java 勉強会 東京', 'yuki', 50), ('Java 勉強会 東京', 'kotone', 40),
  ('Java 勉強会 東京', 'riku', 35), ('Java 勉強会 東京', 'haruka', 60), ('Java 勉強会 東京', 'takumi', 58),
  ('週末 React もくもく会', 'sota', 85), ('週末 React もくもく会', 'hina', 80), ('週末 React もくもく会', 'mio', 70),
  ('週末 React もくもく会', 'sakura', 30), ('週末 React もくもく会', 'kazuki', 28), ('週末 React もくもく会', 'yui', 60),
  ('AWS 初心者ハンズオン', 'ren', 70), ('AWS 初心者ハンズオン', 'hayato', 60), ('AWS 初心者ハンズオン', 'emi', 22),
  ('AWS 初心者ハンズオン', 'tomoya', 18), ('AWS 初心者ハンズオン', 'riku', 30), ('AWS 初心者ハンズオン', 'sho', 50),
  ('Python 読書会', 'rin', 190), ('Python 読書会', 'akari', 180),
  ('デザイン思考ラボ', 'mio', 60), ('デザイン思考ラボ', 'kotone', 40), ('デザイン思考ラボ', 'nozomi', 10),
  ('朝活 英会話クラブ', 'akari', 45), ('朝活 英会話クラブ', 'sakura', 30), ('朝活 英会話クラブ', 'emi', 20),
  ('皇居ランニング部', 'hayato', 40), ('皇居ランニング部', 'yuki', 40), ('皇居ランニング部', 'riku', 30),
  ('皇居ランニング部', 'kazuki', 25),
  ('データ分析もくもく会', 'rin', 50), ('データ分析もくもく会', 'ren', 45), ('データ分析もくもく会', 'tomoya', 15),
  ('Go 言語 入門会', 'sota', 70), ('Go 言語 入門会', 'kazuki', 25), ('Go 言語 入門会', 'nozomi', 10),
  ('アジャイル実践コミュニティ', 'hina', 50), ('アジャイル実践コミュニティ', 'kotone', 35),
  ('写真散歩の会', 'emi', 20), ('写真散歩の会', 'sakura', 25), ('写真散歩の会', 'mio', 40),
  ('セキュリティ勉強会', 'ren', 40), ('セキュリティ勉強会', 'tomoya', 15)
) AS t(c, u, d);

-- ---------- 4. 参加の申請 ----------
INSERT INTO join_requests (community_id, user_id, status, requested_at, decided_at)
SELECT pg_temp.c(c), pg_temp.u(u), s, now() - (h || ' hours')::interval,
       CASE WHEN s <> 'pending' THEN now() - ((h - 2) || ' hours')::interval END
FROM (VALUES
  -- 確認用のアカウントのコミュニティへの、承認待ちの申請（承認・却下を確かめられる）
  ('Java 勉強会 東京', 'emi', 'pending', 20), ('Java 勉強会 東京', 'nozomi', 'pending', 6),
  ('週末 React もくもく会', 'tomoya', 'pending', 30),
  -- 過去に却下した申請（記録として残る）
  ('AWS 初心者ハンズオン', 'nozomi', 'rejected', 200),
  -- 確認用のアカウントが申請して、承認を待っている
  ('セキュリティ勉強会', 'demo', 'pending', 12),
  ('データ分析もくもく会', 'sakura', 'pending', 8)
) AS t(c, u, s, h);

-- ---------- 5. イベント ----------
-- 確認用のアカウントのコミュニティ（それぞれ、開催前と終了済みのイベントを持つ）
SELECT pg_temp.ev('Java 勉強会 東京', 'Spring Boot 入門ハンズオン',
  'Spring Boot で、はじめての REST API を作ります。ノートパソコンと JDK 21 を用意してください。',
  9, 19, 2, 24, '東京・渋谷（会議室 A）', 20, 'auto', 'auto');
SELECT pg_temp.ev('Java 勉強会 東京', 'Java 21 の新機能を読む会',
  'record、パターンマッチ、仮想スレッドを、サンプルを動かしながら読みます。メンバーは承認制です。',
  16, 19, 2, 24, 'オンライン（Zoom）', 10, 'manual', 'closed');
SELECT pg_temp.ev('Java 勉強会 東京', '設計レビュー会',
  '持ち寄ったクラス設計を、みんなでレビューします。少人数で行います。',
  5, 19.5, 2, 24, '東京・渋谷（会議室 B）', 5, 'auto', 'closed');
SELECT pg_temp.ev('Java 勉強会 東京', '新メンバー歓迎会',
  '最近参加したメンバーの歓迎会です。申込みは締め切りました。',
  1, 19, 2, 48, '東京・渋谷（カフェ）', 15, 'auto', 'closed');
SELECT pg_temp.ev('Java 勉強会 東京', '第10回 Java 勉強会',
  'Stream API の使い方を学びました。',
  -12, 19, 2, 24, '東京・渋谷（会議室 A）', 20, 'auto', 'closed');

SELECT pg_temp.ev('週末 React もくもく会', 'React 19 もくもく会',
  'それぞれ作りたいものを作ります。最後に、作ったものを1人3分で紹介します。',
  4, 13, 4, 24, 'オンライン（Discord）', 15, 'auto', 'auto');
SELECT pg_temp.ev('週末 React もくもく会', 'TypeScript 型パズル会',
  'TypeScript の型の問題を、ペアで解きます。',
  11, 13, 3, 24, 'オンライン（Discord）', 12, 'auto', 'manual');
SELECT pg_temp.ev('週末 React もくもく会', 'React Router 入門もくもく会',
  'React Router で、画面の切り替えを作りました。',
  -8, 13, 4, 24, 'オンライン（Discord）', 15, 'auto', 'auto');

SELECT pg_temp.ev('AWS 初心者ハンズオン', 'Terraform で VPC を作る',
  'Terraform で VPC とサブネットを作ります。AWS のアカウントは、こちらで用意します。',
  7, 19, 2.5, 24, '東京・品川（会議室）', 8, 'auto', 'closed');
SELECT pg_temp.ev('AWS 初心者ハンズオン', 'ECS でコンテナを動かす',
  'ECS Fargate に、Docker のコンテナを公開します。メンバー以外も、承認制で参加できます。',
  21, 19, 2.5, 48, '東京・品川（会議室）', 12, 'manual', 'manual');
SELECT pg_temp.ev('AWS 初心者ハンズオン', 'IAM の基本',
  'IAM のユーザー、ロール、ポリシーの違いを学びました。',
  -20, 19, 2, 24, 'オンライン（Zoom）', 30, 'auto', 'auto');

-- ほかのコミュニティ
SELECT pg_temp.ev('デザイン思考ラボ', 'ユーザーインタビュー練習会',
  'インタビューの質問の作り方と、話の聞き方を練習します。', 6, 14, 3, 24, '東京・神田（コワーキング）', 16, 'auto', 'auto');
SELECT pg_temp.ev('デザイン思考ラボ', 'ペルソナを作るワークショップ',
  'インタビューの結果から、ペルソナを作りました。', -15, 14, 3, 24, '東京・神田（コワーキング）', 16, 'auto', 'auto');
SELECT pg_temp.ev('朝活 英会話クラブ', 'Morning English Chat',
  '朝7時から30分、英語で雑談します。今回は、参加の申込みを承認制にしています。', 3, 7, 0.5, 24, 'オンライン（Zoom）', 6, 'manual', 'closed');
SELECT pg_temp.ev('朝活 英会話クラブ', 'English Presentation Practice',
  '英語で3分のプレゼンを練習します。', 13, 7, 1, 24, 'オンライン（Zoom）', 8, 'auto', 'closed');
SELECT pg_temp.ev('皇居ランニング部', '週末 皇居ラン（2周）',
  '桜田門に集合して、2周（10km）走ります。人数が多いので、定員を決めています。', 2, 8, 2, 12, '桜田門', 6, 'auto', 'auto');
SELECT pg_temp.ev('皇居ランニング部', '秋のハーフマラソン練習会',
  '21km を、3つのペースのグループに分かれて走ります。', 18, 7, 3, 24, '桜田門', 20, 'auto', 'auto');
SELECT pg_temp.ev('データ分析もくもく会', '公開データで分析してみよう',
  '気象庁のデータを、pandas で分析します。', 10, 19, 2, 24, 'オンライン（Zoom）', 20, 'auto', 'auto');
SELECT pg_temp.ev('Go 言語 入門会', 'Go で API を作る',
  'net/http で、小さな API を作ります。メンバーも承認制です。', 8, 19, 2, 24, 'オンライン（Zoom）', 10, 'manual', 'manual');
SELECT pg_temp.ev('Go 言語 入門会', 'A Tour of Go を一緒に解く',
  'A Tour of Go の練習問題を解きました。', -6, 19, 2, 24, 'オンライン（Zoom）', 15, 'auto', 'auto');
SELECT pg_temp.ev('アジャイル実践コミュニティ', 'スクラムのふりかえり手法',
  'KPT、Fun/Done/Learn などのふりかえりを体験します。', 14, 19, 2, 24, '東京・大手町（会議室）', 20, 'auto', 'auto');
SELECT pg_temp.ev('写真散歩の会', '紅葉の写真散歩（六義園）',
  '六義園の紅葉を撮りに行きます。', 12, 10, 3, 24, '駒込駅 改札前', 15, 'auto', 'auto');
SELECT pg_temp.ev('写真散歩の会', '夜景の写真散歩（東京駅）',
  '東京駅の丸の内駅舎の夜景を撮りに行きます。', 25, 18, 2, 24, '東京駅 丸の内北口', 12, 'auto', 'auto');
SELECT pg_temp.ev('セキュリティ勉強会', 'OWASP Top 10 を読む会',
  'OWASP Top 10 を1つずつ読みます。', 9, 19, 2, 24, 'オンライン（Zoom）', 30, 'auto', 'closed');

-- ---------- 6. 申込み ----------
-- 確認用のアカウントのイベント（申込者一覧で、いろいろな状態を確かめられる）
SELECT pg_temp.ap('Spring Boot 入門ハンズオン', u, 'applied', h) FROM (VALUES
  ('rin', 200), ('sota', 190), ('hina', 180), ('ren', 170), ('mio', 160), ('hayato', 150),
  ('akari', 140), ('yuki', 130), ('yui', 120), ('sho', 110), ('emi', 60), ('sakura', 50)) AS t(u, h);
-- 承認制：承認待ち 3人、申込み済み 4人、却下 1人
SELECT pg_temp.ap('Java 21 の新機能を読む会', u, s, h) FROM (VALUES
  ('rin', 'applied', 150), ('ren', 'applied', 140), ('yui', 'applied', 130), ('sho', 'applied', 120),
  ('kotone', 'pending', 30), ('riku', 'pending', 20), ('haruka', 'pending', 10), ('hina', 'rejected', 90)) AS t(u, s, h);
-- 満席（定員 5人）で、キャンセル待ち 2人
SELECT pg_temp.ap('設計レビュー会', u, s, h) FROM (VALUES
  ('rin', 'applied', 100), ('sota', 'applied', 90), ('mio', 'applied', 80), ('hayato', 'applied', 70),
  ('takumi', 'applied', 60), ('akari', 'waitlisted', 50), ('kotone', 'waitlisted', 40)) AS t(u, s, h);
-- 締め切り後（開催は明日）
SELECT pg_temp.ap('新メンバー歓迎会', u, 'applied', h) FROM (VALUES
  ('rin', 120), ('sota', 110), ('ren', 90), ('mio', 80), ('kotone', 70), ('riku', 60), ('yui', 50)) AS t(u, h);
-- 終了したイベント（参加の記録）
SELECT pg_temp.ap('第10回 Java 勉強会', u, 'applied', h) FROM (VALUES
  ('rin', 600), ('sota', 590), ('hina', 580), ('ren', 570), ('mio', 560), ('hayato', 550), ('akari', 540),
  ('yuki', 530), ('kotone', 520), ('riku', 510), ('yui', 500), ('sho', 490), ('takumi', 480)) AS t(u, h);
SELECT pg_temp.ap('React 19 もくもく会', u, 'applied', h) FROM (VALUES
  ('sota', 100), ('hina', 90), ('mio', 80), ('sakura', 70), ('kazuki', 60), ('haruka', 50), ('yui', 40), ('nozomi', 20)) AS t(u, h);
SELECT pg_temp.ap('TypeScript 型パズル会', u, s, h) FROM (VALUES
  ('sota', 'applied', 60), ('kazuki', 'applied', 50), ('rin', 'pending', 15)) AS t(u, s, h);
SELECT pg_temp.ap('React Router 入門もくもく会', u, 'applied', h) FROM (VALUES
  ('sota', 400), ('hina', 390), ('mio', 380), ('sakura', 370), ('haruka', 360)) AS t(u, h);
-- 満席（定員 8人）で、キャンセル待ち 1人
SELECT pg_temp.ap('Terraform で VPC を作る', u, s, h) FROM (VALUES
  ('ren', 'applied', 100), ('hayato', 'applied', 95), ('emi', 'applied', 90), ('tomoya', 'applied', 85),
  ('riku', 'applied', 80), ('sho', 'applied', 75), ('ryo', 'applied', 70), ('nanami', 'applied', 65),
  ('rin', 'waitlisted', 30)) AS t(u, s, h);
SELECT pg_temp.ap('ECS でコンテナを動かす', u, s, h) FROM (VALUES
  ('ren', 'applied', 60), ('emi', 'pending', 20), ('hina', 'pending', 10)) AS t(u, s, h);
SELECT pg_temp.ap('IAM の基本', u, 'applied', h) FROM (VALUES
  ('ren', 700), ('hayato', 690), ('emi', 680), ('tomoya', 670), ('riku', 660)) AS t(u, h);

-- 確認用のアカウントが、利用者として申し込んでいる5件（いろいろな状態）
SELECT pg_temp.ap('ユーザーインタビュー練習会', 'demo', 'applied', 40);      -- 申込み済み
SELECT pg_temp.ap('紅葉の写真散歩（六義園）', 'demo', 'applied', 30);          -- 申込み済み
SELECT pg_temp.ap('Go で API を作る', 'demo', 'pending', 18);                 -- 承認待ち
SELECT pg_temp.ap('Morning English Chat', 'demo', 'rejected', 50);          -- 却下された
-- 満席（定員 6人）で、確認用のアカウントはキャンセル待ち 1番目
SELECT pg_temp.ap('週末 皇居ラン（2周）', u, s, h) FROM (VALUES
  ('yuto', 'applied', 80), ('hayato', 'applied', 70), ('yuki', 'applied', 60), ('riku', 'applied', 50),
  ('kazuki', 'applied', 40), ('kenta', 'applied', 35), ('demo', 'waitlisted', 20)) AS t(u, s, h);
-- 終了したイベントにも参加していた
SELECT pg_temp.ap('A Tour of Go を一緒に解く', 'demo', 'applied', 300);
SELECT pg_temp.ap('ペルソナを作るワークショップ', 'demo', 'applied', 500);

-- ほかのイベントの申込み
SELECT pg_temp.ap('ユーザーインタビュー練習会', u, 'applied', h) FROM (VALUES ('mio', 60), ('kotone', 50), ('takumi', 45), ('nozomi', 10)) AS t(u, h);
SELECT pg_temp.ap('Morning English Chat', u, 'applied', h) FROM (VALUES ('akari', 40), ('sakura', 30), ('mai', 25), ('emi', 20)) AS t(u, h);
SELECT pg_temp.ap('English Presentation Practice', u, 'applied', h) FROM (VALUES ('akari', 30), ('mai', 20)) AS t(u, h);
SELECT pg_temp.ap('秋のハーフマラソン練習会', u, 'applied', h) FROM (VALUES ('yuto', 50), ('hayato', 40), ('riku', 30)) AS t(u, h);
SELECT pg_temp.ap('公開データで分析してみよう', u, 'applied', h) FROM (VALUES ('rin', 40), ('ren', 30), ('tomoya', 20)) AS t(u, h);
SELECT pg_temp.ap('Go で API を作る', u, s, h) FROM (VALUES ('sota', 'applied', 40), ('saki', 'applied', 35), ('kazuki', 'pending', 10)) AS t(u, s, h);
SELECT pg_temp.ap('A Tour of Go を一緒に解く', u, 'applied', h) FROM (VALUES ('sota', 310), ('saki', 305), ('kazuki', 290)) AS t(u, h);
SELECT pg_temp.ap('スクラムのふりかえり手法', u, 'applied', h) FROM (VALUES ('hina', 30), ('kotone', 20)) AS t(u, h);
SELECT pg_temp.ap('紅葉の写真散歩（六義園）', u, 'applied', h) FROM (VALUES ('kaito', 60), ('emi', 40), ('sakura', 35), ('mio', 25)) AS t(u, h);
SELECT pg_temp.ap('夜景の写真散歩（東京駅）', u, 'applied', h) FROM (VALUES ('kaito', 20), ('mio', 10)) AS t(u, h);
SELECT pg_temp.ap('OWASP Top 10 を読む会', u, 'applied', h) FROM (VALUES ('yui', 30), ('ren', 25), ('tomoya', 15)) AS t(u, h);

-- ---------- 7. お知らせ ----------
INSERT INTO notifications (user_id, type, event_title, community_id, event_id, actor_user_id, read_at, created_at)
SELECT pg_temp.u(u), t, title, pg_temp.c(c), CASE WHEN t = 'event_deleted' THEN NULL ELSE pg_temp.e(title) END,
       CASE WHEN actor IS NULL THEN NULL ELSE pg_temp.u(actor) END,
       CASE WHEN is_read THEN now() - ((h - 1) || ' hours')::interval END, now() - (h || ' hours')::interval
FROM (VALUES
  -- 確認用のアカウントへのお知らせ（未読 3件、既読 2件）
  ('demo', 'application_rejected', 'Morning English Chat', '朝活 英会話クラブ', NULL, false, 6),
  ('demo', 'event_deleted', '春の写真散歩（新宿御苑）', '写真散歩の会', NULL, false, 10),
  ('demo', 'late_cancellation', '新メンバー歓迎会', 'Java 勉強会 東京', 'hina', false, 3),
  ('demo', 'application_approved', 'ペルソナを作るワークショップ', 'デザイン思考ラボ', NULL, true, 520),
  ('demo', 'application_approved', 'A Tour of Go を一緒に解く', 'Go 言語 入門会', NULL, true, 320),
  -- ほかの人へのお知らせ
  ('hina', 'application_rejected', 'Java 21 の新機能を読む会', 'Java 勉強会 東京', NULL, false, 80),
  ('rin', 'application_approved', 'Java 21 の新機能を読む会', 'Java 勉強会 東京', NULL, true, 140),
  ('ren', 'application_approved', 'Java 21 の新機能を読む会', 'Java 勉強会 東京', NULL, true, 130),
  ('yui', 'late_cancellation', '新メンバー歓迎会', 'Java 勉強会 東京', 'hina', false, 3),
  ('sho', 'late_cancellation', '新メンバー歓迎会', 'Java 勉強会 東京', 'hina', true, 3)
) AS t(u, t, title, c, actor, is_read, h);

-- ---------- 8. 片付け ----------
-- パスワードの変換に使った道具は、もう使わないので消す
DROP EXTENSION pgcrypto;

COMMIT;

-- 入れたデータの数
SELECT 'users' AS テーブル, count(*) AS 件数 FROM users
UNION ALL SELECT 'communities', count(*) FROM communities
UNION ALL SELECT 'memberships', count(*) FROM memberships
UNION ALL SELECT 'join_requests', count(*) FROM join_requests
UNION ALL SELECT 'events', count(*) FROM events
UNION ALL SELECT 'applications', count(*) FROM applications
UNION ALL SELECT 'notifications', count(*) FROM notifications;

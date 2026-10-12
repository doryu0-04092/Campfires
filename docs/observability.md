# オブザーバビリティ（ログ・メトリクス・トレース）

<!--
  【役割】本番で、アプリの様子を外から確かめるための仕組み（オブザーバビリティ）の説明。
  【なぜ必要か】エラーが増えた・遅くなった・止まった、に利用者より先に気づき、どこが原因かを調べられるようにするため。
  何を、どこで見られるか、手元でどう確かめるかを残す。
-->

## 1. 全体

| 要素 | 何が分かるか | どう作るか | 本番で見る場所 |
|---|---|---|---|
| ログ | いつ・だれが・どの API を呼び・結果と時間はどうだったか。ログインの失敗、想定していないエラーの中身 | アプリが JSON で書き出す（Spring Boot の structured logging）。AWS が CloudWatch Logs に集める | CloudWatch Logs（`/ecs/campfires`）、Logs Insights の保存したクエリ |
| メトリクス | リクエストの数、5xx の数、応答の時間（p50/p95/p99）、ログインの失敗の数、CPU・メモリ、データベースの様子 | アプリのログから数字を作る（メトリクスフィルター）＋ AWS が無料で取っている数字（ECS、RDS） | CloudWatch のダッシュボード `campfires`、アラーム（メールで知らせる） |
| トレース | 1つのリクエストが、API の中とデータベース（SQL ごと）で、どれだけ時間を使ったか | 箱に入れた ADOT Java エージェント（AWS が配る OpenTelemetry の部品）が、コードを変えずに記録し、X-Ray に送る | CloudWatch の Transaction Search（トレース） |

3つは **トレースの番号（`trace_id`）** でつながります。ログの1行ごとに `trace_id` が入るので、アラーム → ダッシュボード → ログ → トレースの順にたどれます。

## 2. ログ

本番（`prod` のプロファイル）では、1行ずつの JSON で書き出します（`application-prod.properties` の `logging.structured.format.console=ecs`）。

アクセスログ（`AccessLogFilter`。API の呼び出し1回で1行）の例：

```json
{"@timestamp":"…","log":{"level":"INFO","logger":"campfires.access"},"message":"POST /api/auth/login 200 83ms",
 "trace_id":"6acbefda3d6300fcdda528a0d87e4947","span_id":"74307c8f6b65dbfd",
 "method":"POST","path":"/api/auth/login","status":200,"durationMs":83,"clientIp":"…","userId":1}
```

| ログ | 中身 | 書くところ |
|---|---|---|
| アクセスログ | method、path、status、durationMs、clientIp、userId（ログインしているとき） | `AccessLogFilter` |
| ログイン・登録 | `event` が signup、login、login_failed、login_locked、userId | `AuthService` |
| 回数制限 | `event` が rate_limited、path、clientIp | `RateLimitFilter` |
| アカウントの削除 | `event` が account_deleted、userId | `AccountService` |
| 想定していないエラー | ERROR、例外の中身（スタックトレース） | `GlobalExceptionHandler` |

**ログに書かないもの：** パスワード、メールアドレス、本名、送られてきた中身、URL の `?` より後ろ。個人の情報と秘密の値をログに広げないためです。利用者は `userId`（番号）で表します。

**ログに書かないリクエスト：** 画面のファイル（HTML、JavaScript）と、うまくいった死活の確認（`/api/health`）。数が多く、調べるときの役に立たないためです。

ログは 14日で消えます（`infra/ecs.tf`）。

## 3. メトリクスとアラーム

`infra/monitoring.tf` で作ります。アラームはメール（SNS）で知らせます。異常が直ったときも知らせが届きます。

| アラーム | 条件 | まず見るところ |
|---|---|---|
| app-not-running | 箱が1つも動いていない（ECS の数字が10分届かない） | ECS のサービスの画面（イベント、止まった理由） |
| server-errors | 5xx が 5分間に 5回以上 | 保存したクエリ `campfires/errors` |
| error-logs | 想定していないエラーのログが1回でも出た | 保存したクエリ `campfires/errors` |
| slow-responses | 応答の時間の p95 が 2秒超えで15分続いた | `campfires/slow-requests` → その trace_id のトレース |
| login-failures | ログインの失敗が 5分間に 50回以上 | `campfires/security-events`（IP アドレスごと） |
| app-memory | 箱のメモリが 85% 超えで10分続いた | ダッシュボード、ログ |
| db-cpu | データベースの CPU が 80% 超えで15分続いた | トレースで、時間のかかっている SQL |
| db-storage | データベースの空き容量が 2GB 未満 | RDS の画面 |

**ロードバランサーの数字を使わない理由：** ロードバランサーは Express Mode が作るので、Terraform から名前を指してアラームを作れません。同じ数字（リクエストの数、5xx、応答の時間）を、アプリのアクセスログから作っています。ロードバランサーの数字は、AWS の画面（EC2 → ロードバランサー → モニタリング）で見られます。

**JVM の中の数字（ヒープ、データベースの接続の数など）を送らない理由：** Spring Boot 4 には、CloudWatch に送る仕組みが入っていません（Spring Cloud AWS が必要）。箱のメモリとデータベースの接続の数は、AWS の数字で見ています。

## 4. トレース

- 部品：ADOT Java エージェント 2.31.0（`Dockerfile`。版と、ファイルの指紋（SHA-256）を決めて入れる）
- 送り先：X-Ray の OTLP の受け口 `https://xray.ap-northeast-1.amazonaws.com/v1/traces`。箱のタスクロールに `AWSXrayWriteOnlyAccess` を付けて、送る権限を渡す（`infra/iam.tf`）
- 見る場所：Transaction Search（`infra/tracing.tf` で有効にする。すべてのトレースが `aws/spans` のロググループに入り、14日で消える。`tracing.tf` の ③）
- 記録する割合：`infra/variables.tf` の `trace_sampling_ratio`（はじめは 1 ＝ すべて。利用者が増えたら 0.05 にする）

記録される中身の例（`GET /api/communities`）：

```
GET /api/communities                 60ms
  ├ SELECT（セッション）               1ms
  ├ SELECT campfires.users            1ms
  ├ SELECT count(*) …（件数）          1ms
  └ SELECT s.id, s.name …（一覧）      1ms
```

SQL は、値が `?` に置き換えられた形で記録されます（個人の情報は入りません）。URL の `?` より後ろ（検索の言葉など）は、トレースには記録されます。秘密の値（パスワード、CSRF トークン、セッション）は、URL には入れていません。

**決めたこと：**

| 設定 | 値 | 理由 |
|---|---|---|
| `OTEL_TRACES_SAMPLER` | `traceidratio` | 外から送られてきたヘッダーで、「記録しない」「記録する」を変えられないようにする |
| `OTEL_PROPAGATORS` | `tracecontext` | ロードバランサーが付ける `X-Amzn-Trace-Id` を使わない。手元で、このヘッダーを付けても記録されることを確かめた |
| `OTEL_AWS_APPLICATION_SIGNALS_ENABLED` | `false` | AWS の説明で、Transaction Search と料金が重なるため |
| 手元・自動テストでの送り先 | なし（`Dockerfile` の既定が `none`） | 送り先は、動かす場所で決める。送らなくても、ログには trace_id が入る |

## 5. 手元で確かめる

```bash
# トレースを受け取る Jaeger（画面は http://localhost:16686 ）
docker run -d --name cf-jaeger --network cf-prod -p 16686:16686 jaegertracing/jaeger:2.9.0

# 本番用の箱を、Jaeger に送る設定で動かす（データベースの準備は README の「本番用の箱を手元で動かす」）
docker run -d --name cf-prod-app --network cf-prod -p 8081:8080 \
  -e DB_HOST=cf-prod-db -e DB_USERNAME=campfires -e DB_PASSWORD=（手元のパスワード） -e DB_SSL_MODE=disable \
  -e OTEL_TRACES_EXPORTER=otlp -e OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://cf-jaeger:4318/v1/traces \
  campfires

# ログ（JSON）を見る
docker logs -f cf-prod-app
```

確かめたこと（2026年10月）：

- ログが JSON で出て、アクセスログ・ログインの出来事に `trace_id` が入る
- Jaeger に、API → SQL のトレースが届き、ログの `trace_id` と同じ番号で探せる
- CloudWatch のメトリクスフィルターの条件が、実際のログの行に合う（`aws logs test-metric-filter`。AWS に何も作らない確認の API）

## 6. 料金の目安（東京、1か月）

| もの | 目安 |
|---|---|
| ログから作る数字（6つ） | 約 1.8ドル |
| アラーム（8つ） | 約 0.8ドル |
| ダッシュボード | 無料（3枚まで） |
| ログとトレースの取り込み・保存 | 利用者が少なければ 1ドル未満 |

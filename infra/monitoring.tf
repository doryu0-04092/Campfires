# 【役割】本番のアプリを見張る設定：ログから数字（メトリクス）を作り、ダッシュボード（1枚の画面）にまとめ、
# 異常があればメールで知らせる（アラーム）。よく使うログの検索の文も保存する。
# 【なぜ必要か】本番で、エラーが増えた・遅くなった・止まった、を利用者より先に気づき、原因を調べられるようにするため
# （オブザーバビリティの3要素のうち、メトリクスとログ。トレースは tracing.tf。全体は docs/observability.md）。
#
# 数字の出どころ：
# - アプリのログ（AccessLogFilter が出す1行のログ）から作る数字：リクエストの数、5xx の数、応答の時間、エラーのログ、ログインの失敗
#   → ロードバランサーは Express Mode が作るので、Terraform から名前を指せない。アプリのログから作れば、確実に取れる
# - AWS が無料で取っている数字：ECS（CPU、メモリ）、RDS（CPU、接続の数、空き容量）
#
# 料金の目安（東京）：ログから作る数字 1つ月 0.3ドル×6、アラーム 1つ月 0.1ドル×8、ダッシュボードは3枚まで無料。

locals {
  # ログから作る数字の置き場所（名前空間）
  metric_namespace = "Campfires"
  # アクセスログ（AccessLogFilter）だけを選ぶ条件。ログは JSON なので、$.項目 で中身を指せる
  access_log = "$.log.logger = \"campfires.access\""
  # ECS のサービスの名前。Express Mode が作ったサービスの ARN（arn:aws:ecs:地域:番号:service/クラスター/名前）の最後の部分
  ecs_service_name = element(split("/", aws_ecs_express_gateway_service.app.service_arn), 2)
}

# ---------- 知らせの送り先（SNS のトピックと、メール） ----------
# 作ったあと、AWS から「購読の確認」のメールが届く。リンクを押すまで、知らせは届かない
resource "aws_sns_topic" "alerts" {
  name = "${var.app_name}-alerts"
}

resource "aws_sns_topic_subscription" "alerts_email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# ---------- ログから数字を作る（メトリクスフィルター） ----------
# リクエストの数（1行ごとに 1 を数える）
resource "aws_cloudwatch_log_metric_filter" "requests" {
  name           = "${var.app_name}-requests"
  log_group_name = aws_cloudwatch_log_group.app.name
  pattern        = "{ ${local.access_log} }"
  metric_transformation {
    name      = "ApiRequests"
    namespace = local.metric_namespace
    value     = "1"
    unit      = "Count"
  }
}

# サーバーのエラー（結果が 500 番台）の数
resource "aws_cloudwatch_log_metric_filter" "server_errors" {
  name           = "${var.app_name}-server-errors"
  log_group_name = aws_cloudwatch_log_group.app.name
  pattern        = "{ ${local.access_log} && $.status >= 500 }"
  metric_transformation {
    name      = "ApiServerErrors"
    namespace = local.metric_namespace
    value     = "1"
    unit      = "Count"
  }
}

# 応答の時間（ミリ秒）。ログの durationMs の値を、そのまま数字にする
resource "aws_cloudwatch_log_metric_filter" "latency" {
  name           = "${var.app_name}-latency"
  log_group_name = aws_cloudwatch_log_group.app.name
  pattern        = "{ ${local.access_log} }"
  metric_transformation {
    name      = "ApiLatency"
    namespace = local.metric_namespace
    value     = "$.durationMs"
    unit      = "Milliseconds"
  }
}

# ERROR のログの数（想定していないエラー。GlobalExceptionHandler などが書く）
resource "aws_cloudwatch_log_metric_filter" "error_logs" {
  name           = "${var.app_name}-error-logs"
  log_group_name = aws_cloudwatch_log_group.app.name
  pattern        = "{ $.log.level = \"ERROR\" }"
  metric_transformation {
    name      = "ErrorLogs"
    namespace = local.metric_namespace
    value     = "1"
    unit      = "Count"
  }
}

# ログインの失敗の数（パスワードを次々に試す攻撃に気づくため。AuthService が書く）
resource "aws_cloudwatch_log_metric_filter" "login_failures" {
  name           = "${var.app_name}-login-failures"
  log_group_name = aws_cloudwatch_log_group.app.name
  pattern        = "{ $.event = \"login_failed\" }"
  metric_transformation {
    name      = "LoginFailures"
    namespace = local.metric_namespace
    value     = "1"
    unit      = "Count"
  }
}

# 回数制限で断った数（RateLimitFilter が書く）
resource "aws_cloudwatch_log_metric_filter" "rate_limited" {
  name           = "${var.app_name}-rate-limited"
  log_group_name = aws_cloudwatch_log_group.app.name
  pattern        = "{ $.event = \"rate_limited\" }"
  metric_transformation {
    name      = "RateLimited"
    namespace = local.metric_namespace
    value     = "1"
    unit      = "Count"
  }
}

# ---------- アラーム（異常のときにメールで知らせる） ----------
# 共通：5分ごとに数字をまとめて判断する。異常が直ったときも知らせる（ok_actions）

# アプリが動いていない：ECS の CPU の数字が届かない（箱が1つも動いていない）と、異常にする。
# treat_missing_data = "breaching" は「数字が届かないことを、異常として扱う」設定
resource "aws_cloudwatch_metric_alarm" "app_not_running" {
  alarm_name          = "${var.app_name}-app-not-running"
  alarm_description   = "アプリの箱が動いていません（ECS の数字が届いていません）"
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  dimensions          = { ClusterName = aws_ecs_cluster.main.name, ServiceName = local.ecs_service_name }
  statistic           = "SampleCount"
  period              = 300
  evaluation_periods  = 2
  comparison_operator = "LessThanThreshold"
  threshold           = 1
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
  # アプリを作り終わってから見張り始める（作っている途中で、異常の知らせが届かないように）
  depends_on = [aws_ecs_express_gateway_service.app]
}

# サーバーのエラーが、5分間に 5回以上
resource "aws_cloudwatch_metric_alarm" "server_errors" {
  alarm_name          = "${var.app_name}-server-errors"
  alarm_description   = "API のサーバーのエラー（5xx）が増えています。ログの検索「campfires/errors」で原因を調べてください"
  namespace           = local.metric_namespace
  metric_name         = "ApiServerErrors"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 5
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# 想定していないエラーのログが、1回でも出た
resource "aws_cloudwatch_metric_alarm" "error_logs" {
  alarm_name          = "${var.app_name}-error-logs"
  alarm_description   = "想定していないエラーのログが出ました。ログの検索「campfires/errors」で原因を調べてください"
  namespace           = local.metric_namespace
  metric_name         = "ErrorLogs"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# 遅い：応答の時間の 95%（遅いほうから 5% を除いた値）が、15分続けて 2秒を超えた
resource "aws_cloudwatch_metric_alarm" "slow_responses" {
  alarm_name          = "${var.app_name}-slow-responses"
  alarm_description   = "API の応答が遅くなっています（p95 が 2秒超え）。ログの検索「campfires/slow-requests」とトレースで原因を調べてください"
  namespace           = local.metric_namespace
  metric_name         = "ApiLatency"
  extended_statistic  = "p95"
  period              = 300
  evaluation_periods  = 3
  comparison_operator = "GreaterThanThreshold"
  threshold           = 2000
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# ログインの失敗が、5分間に 50回以上（パスワードを次々に試す攻撃のおそれ）
resource "aws_cloudwatch_metric_alarm" "login_failures" {
  alarm_name          = "${var.app_name}-login-failures"
  alarm_description   = "ログインの失敗が増えています。ログの検索「campfires/security-events」で、IP アドレスを確かめてください"
  namespace           = local.metric_namespace
  metric_name         = "LoginFailures"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 50
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# 箱のメモリが、10分続けて 85% を超えた（Java のメモリ不足で止まる前に気づくため）
resource "aws_cloudwatch_metric_alarm" "app_memory" {
  alarm_name          = "${var.app_name}-app-memory"
  alarm_description   = "アプリの箱のメモリが足りなくなりそうです"
  namespace           = "AWS/ECS"
  metric_name         = "MemoryUtilization"
  dimensions          = { ClusterName = aws_ecs_cluster.main.name, ServiceName = local.ecs_service_name }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  comparison_operator = "GreaterThanThreshold"
  threshold           = 85
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# データベースの CPU が、15分続けて 80% を超えた
resource "aws_cloudwatch_metric_alarm" "db_cpu" {
  alarm_name          = "${var.app_name}-db-cpu"
  alarm_description   = "データベースの CPU が高い状態が続いています。トレースで、時間のかかっている SQL を確かめてください"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.main.identifier }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  comparison_operator = "GreaterThanThreshold"
  threshold           = 80
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# データベースの空き容量が 2GB を下回った（20GB のうち）
resource "aws_cloudwatch_metric_alarm" "db_storage" {
  alarm_name          = "${var.app_name}-db-storage"
  alarm_description   = "データベースの空き容量が少なくなっています"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.main.identifier }
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "LessThanThreshold"
  threshold           = 2 * 1024 * 1024 * 1024
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# ---------- ダッシュボード（1枚の画面に、主な数字をまとめる） ----------
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = var.app_name
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric", x = 0, y = 0, width = 8, height = 6
        properties = {
          title  = "API のリクエスト数と 5xx（5分ごと）"
          region = var.region
          stat   = "Sum"
          period = 300
          metrics = [
            [local.metric_namespace, "ApiRequests"],
            [local.metric_namespace, "ApiServerErrors"],
          ]
        }
      },
      {
        type = "metric", x = 8, y = 0, width = 8, height = 6
        properties = {
          title  = "API の応答の時間（ミリ秒）"
          region = var.region
          period = 300
          metrics = [
            [local.metric_namespace, "ApiLatency", { stat = "p50", label = "p50" }],
            [local.metric_namespace, "ApiLatency", { stat = "p95", label = "p95" }],
            [local.metric_namespace, "ApiLatency", { stat = "p99", label = "p99" }],
          ]
        }
      },
      {
        type = "metric", x = 16, y = 0, width = 8, height = 6
        properties = {
          title  = "エラーのログ・ログインの失敗・回数制限"
          region = var.region
          stat   = "Sum"
          period = 300
          metrics = [
            [local.metric_namespace, "ErrorLogs"],
            [local.metric_namespace, "LoginFailures"],
            [local.metric_namespace, "RateLimited"],
          ]
        }
      },
      {
        type = "metric", x = 0, y = 6, width = 8, height = 6
        properties = {
          title  = "アプリの箱（ECS）の CPU とメモリ（%）"
          region = var.region
          stat   = "Average"
          period = 300
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", aws_ecs_cluster.main.name, "ServiceName", local.ecs_service_name],
            ["AWS/ECS", "MemoryUtilization", "ClusterName", aws_ecs_cluster.main.name, "ServiceName", local.ecs_service_name],
          ]
        }
      },
      {
        type = "metric", x = 8, y = 6, width = 8, height = 6
        properties = {
          title  = "データベース（RDS）の CPU（%）と接続の数"
          region = var.region
          stat   = "Average"
          period = 300
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", aws_db_instance.main.identifier],
            ["AWS/RDS", "DatabaseConnections", "DBInstanceIdentifier", aws_db_instance.main.identifier, { yAxis = "right" }],
          ]
        }
      },
      {
        type = "metric", x = 16, y = 6, width = 8, height = 6
        properties = {
          title  = "データベースの空き容量（バイト）"
          region = var.region
          stat   = "Minimum"
          period = 300
          metrics = [
            ["AWS/RDS", "FreeStorageSpace", "DBInstanceIdentifier", aws_db_instance.main.identifier],
          ]
        }
      },
      {
        type = "log", x = 0, y = 12, width = 24, height = 6
        properties = {
          title  = "最近のエラー（WARN と ERROR のログ）"
          region = var.region
          query  = "SOURCE '${aws_cloudwatch_log_group.app.name}' | fields @timestamp, log.level, message, path, status, userId, trace_id | filter log.level in [\"WARN\", \"ERROR\"] | sort @timestamp desc | limit 50"
          view   = "table"
        }
      },
    ]
  })
}

# ---------- 保存したログの検索の文（CloudWatch Logs Insights） ----------
# CloudWatch の画面の「Logs Insights」の「保存したクエリ」から、すぐに使える

# エラーを探す（500 番台と ERROR のログ）。trace_id で、トレースの画面から、同じリクエストの流れを見られる
resource "aws_cloudwatch_query_definition" "errors" {
  name            = "campfires/errors"
  log_group_names = [aws_cloudwatch_log_group.app.name]
  query_string    = <<-EOT
    fields @timestamp, log.level, log.logger, message, path, status, userId, trace_id, error.message, error.stack_trace
    | filter log.level = "ERROR" or status >= 500
    | sort @timestamp desc
    | limit 100
  EOT
}

# 遅いリクエストを探す（1秒以上）
resource "aws_cloudwatch_query_definition" "slow_requests" {
  name            = "campfires/slow-requests"
  log_group_names = [aws_cloudwatch_log_group.app.name]
  query_string    = <<-EOT
    fields @timestamp, method, path, status, durationMs, userId, trace_id
    | filter log.logger = "campfires.access" and durationMs >= 1000
    | sort durationMs desc
    | limit 100
  EOT
}

# API ごとの、リクエストの数と応答の時間
resource "aws_cloudwatch_query_definition" "api_summary" {
  name            = "campfires/api-summary"
  log_group_names = [aws_cloudwatch_log_group.app.name]
  query_string    = <<-EOT
    filter log.logger = "campfires.access"
    | stats count(*) as requests, avg(durationMs) as avgMs, pct(durationMs, 95) as p95Ms, max(durationMs) as maxMs by method, path
    | sort requests desc
  EOT
}

# セキュリティの出来事（ログインの失敗、ロック、回数制限）を、IP アドレスごとに数える
resource "aws_cloudwatch_query_definition" "security_events" {
  name            = "campfires/security-events"
  log_group_names = [aws_cloudwatch_log_group.app.name]
  query_string    = <<-EOT
    fields @timestamp, event, userId, clientIp, trace_id
    | filter event in ["login_failed", "login_locked", "rate_limited"]
    | sort @timestamp desc
    | limit 200
  EOT
}

# 1つのリクエストのログを、トレースの番号で探す（trace_id の値を書き換えて使う）
resource "aws_cloudwatch_query_definition" "by_trace" {
  name            = "campfires/by-trace-id"
  log_group_names = [aws_cloudwatch_log_group.app.name]
  query_string    = <<-EOT
    fields @timestamp, log.level, log.logger, message, status, durationMs, userId
    | filter trace_id = "ここにトレースの番号を入れる"
    | sort @timestamp asc
  EOT
}

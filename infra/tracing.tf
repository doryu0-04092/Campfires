# 【役割】トレース（1つのリクエストが、API とデータベースのどこで時間を使ったかの記録）を、AWS で受け取る設定。
# 【なぜ必要か】箱の中のトレースの部品（ADOT Java エージェント。Dockerfile）が送ったトレースを、
# CloudWatch の Transaction Search で見られるようにするため（docs/observability.md）。
#
# AWS の説明（Getting started with Transaction Search、Exporting collector-less telemetry using ADOT SDK）の手順どおり：
# ① X-Ray が、トレースを CloudWatch Logs（aws/spans）に書き込んでよい、という許可（リソースポリシー）
# ② X-Ray が受け取ったトレースの行き先を、CloudWatch Logs にする（Transaction Search を有効にする）
# ③ 箱に、トレースを送る権限を付ける（iam.tf の task ロール）
#
# 注意：② は、この AWS アカウントの東京リージョン全体の設定。terraform destroy で、この設定の Terraform の管理をやめても、
# AWS の設定は CloudWatch Logs のまま残る（AWS Provider の説明）。元に戻すときは、
# aws xray update-trace-segment-destination --destination XRay を実行する（infra/README.md）。

# アカウントの番号は、github_oidc.tf の data.aws_caller_identity.current を使う（番号そのものは、ファイルに書かない）

# ① X-Ray が、トレースを CloudWatch Logs に書き込んでよい、という許可
resource "aws_cloudwatch_log_resource_policy" "transaction_search" {
  policy_name = "${var.app_name}-transaction-search"
  policy_document = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "TransactionSearchXRayAccess"
      Effect    = "Allow"
      Principal = { Service = "xray.amazonaws.com" }
      Action    = "logs:PutLogEvents"
      Resource = [
        "arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:aws/spans:*",
        "arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:/aws/application-signals/data:*",
      ]
      # このアカウントの X-Ray からの書き込みだけを許す（ほかのアカウントに使われないようにする）
      Condition = {
        ArnLike      = { "aws:SourceArn" = "arn:aws:xray:${var.region}:${data.aws_caller_identity.current.account_id}:*" }
        StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }
      }
    }]
  })
}

# ② トレースの行き先を CloudWatch Logs にする（Transaction Search）。
# 受け取ったトレースは、すべて aws/spans のロググループに入り、Transaction Search で探せる。
# そのうち 1%（無料の範囲）が「トレースの要約」として X-Ray の画面にも出る（AWS の既定）
resource "aws_xray_trace_segment_destination" "main" {
  destination = "CloudWatchLogs"
  depends_on  = [aws_cloudwatch_log_resource_policy.transaction_search]
}

# ③ トレースの置き場所（aws/spans）を、14日で消す（アプリのログと同じ期間。置いておく分だけ料金がかかるため）。
# このロググループは、② を有効にしたときに AWS が自動で作る（作られたときは 30日）。import で Terraform の管理に取り込み、
# 保存の期間を、コードとして残す。
# 注意：terraform destroy をすると、このロググループ（トレースの記録）も消える。
import {
  to = aws_cloudwatch_log_group.spans
  id = "aws/spans"
}

resource "aws_cloudwatch_log_group" "spans" {
  name              = "aws/spans"
  retention_in_days = 14
  # ② のあとに作られるものなので、② が終わってから扱う
  depends_on = [aws_xray_trace_segment_destination.main]
}

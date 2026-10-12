# 【役割】ECS が使う権限（IAM ロール）を作るファイル。
# 【なぜ必要か】ECS Express Mode には、2つの権限が必要（AWS の決まり）。
# - 実行ロール（execution role）：ECS が、イメージを取ってくる、ログを書く、パスワードを Secrets Manager から読むための権限
# - インフラロール（infrastructure role）：ECS が、ロードバランサーや自動の台数調整などを作り、管理するための権限
# それぞれ、AWS が用意している決まった権限（AWS 管理ポリシー）を付け、足りない分だけを足す（必要なものだけを渡す）。

# ---------- 実行ロール ----------
resource "aws_iam_role" "execution" {
  name = "${var.app_name}-ecs-execution"
  # このロールを使ってよいのは、ECS のタスク（箱）だけ
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# イメージの取得と、ログの書き込みの権限（AWS 管理ポリシー）
resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# データベースのパスワード（Secrets Manager の campfires/db）を読む権限。このパスワードだけに限る
resource "aws_iam_role_policy" "execution_db_secret" {
  name = "read-db-secret"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "secretsmanager:GetSecretValue"
      Resource = aws_secretsmanager_secret.db.arn
    }]
  })
}

# ---------- タスクロール（箱の中のアプリが使う権限） ----------
# 実行ロールは「ECS が箱を準備する」ための権限。タスクロールは「箱の中のアプリ自身」が AWS を使うための権限。
# アプリ（トレースの部品）が、トレースを X-Ray に送るために使う（tracing.tf、docs/observability.md）
resource "aws_iam_role" "task" {
  name = "${var.app_name}-ecs-task"
  # このロールを使ってよいのは、ECS のタスク（箱）だけ
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# トレースを X-Ray に送る権限（AWS 管理ポリシー。書き込みだけで、読むことはできない）。
# AWS の説明（Exporting collector-less telemetry using ADOT SDK）で、この権限を付けるように書かれている
resource "aws_iam_role_policy_attachment" "task_xray" {
  role       = aws_iam_role.task.name
  policy_arn = "arn:aws:iam::aws:policy/AWSXrayWriteOnlyAccess"
}

# ---------- インフラロール ----------
resource "aws_iam_role" "infrastructure" {
  name = "${var.app_name}-ecs-infrastructure"
  # このロールを使ってよいのは、ECS だけ
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# ロードバランサー、証明書、セキュリティグループ、自動の台数調整を作る権限（AWS 管理ポリシー）
resource "aws_iam_role_policy_attachment" "infrastructure_managed" {
  role       = aws_iam_role.infrastructure.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSInfrastructureRoleforExpressGatewayServices"
}

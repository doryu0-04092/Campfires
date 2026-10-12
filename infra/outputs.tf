# 【役割】terraform apply が終わったあとに表示する値（出力）。
# 【なぜ必要か】公開した URL や、イメージを上げる場所など、次の作業で使う値を、すぐ確かめられるようにするため。
# あとから見るときは terraform output。

output "app_url" {
  description = "公開した URL（ECS Express Mode が作った、HTTPS の URL）"
  # endpoint には、https:// から始まる URL がそのまま入っている
  value = aws_ecs_express_gateway_service.app.ingress_paths[0].endpoint
}

output "ecr_repository_url" {
  description = "Docker イメージを上げる場所（ECR のリポジトリ）"
  value       = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  description = "ECS のクラスターの名前（CD で使う）"
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_arn" {
  description = "ECS のサービスの ARN（CD で使う）"
  value       = aws_ecs_express_gateway_service.app.service_arn
}

output "github_deploy_role_arn" {
  description = "CD（GitHub Actions）が使う IAM ロールの ARN（GitHub のリポジトリの設定に登録する）"
  value       = aws_iam_role.github_deploy.arn
}

output "db_secret_arn" {
  description = "データベースのパスワードを保存した Secrets Manager の ARN（パスワードそのものは表示しない）"
  value       = aws_secretsmanager_secret.db.arn
}

output "dashboard_url" {
  description = "見張りのダッシュボード（CloudWatch）の URL"
  value       = "https://${var.region}.console.aws.amazon.com/cloudwatch/home?region=${var.region}#dashboards/dashboard/${aws_cloudwatch_dashboard.main.dashboard_name}"
}

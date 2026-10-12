# 【役割】Docker イメージ（アプリの箱）を置いておく場所（ECR のリポジトリ）を作るファイル。
# 【なぜ必要か】ECS は、ここに置いたイメージを取ってきて動かす。CD（GitHub Actions）も、ここに新しいイメージを上げる。

resource "aws_ecr_repository" "app" {
  name = var.app_name
  # 同じタグ（名前）のイメージを、上書きできないようにする（どのイメージが動いているか、後から分かるように）
  image_tag_mutability = "IMMUTABLE"
  # terraform destroy で全部消すとき、中のイメージも一緒に消す（ポートフォリオなので、消しやすさを優先する）
  force_delete = true

  # イメージを上げたときに、中のソフトの弱点（脆弱性）を調べる
  image_scanning_configuration {
    scan_on_push = true
  }
}

# 古いイメージを自動で消す決まり（置いておく分だけ料金がかかるため、新しい 10個だけを残す）
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep only the latest 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

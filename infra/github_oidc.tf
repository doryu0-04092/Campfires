# 【役割】CD（GitHub Actions）が AWS を操作するための権限（IAM ロール）を作るファイル。
# 【なぜ必要か】main にマージされたら、GitHub Actions が新しいイメージを ECR に上げ、ECS の箱を入れ替える。
# AWS のアクセスキー（ずっと使えるパスワードのようなもの）を GitHub に保存すると、漏れたときに危ない。
# そこで OIDC を使う：GitHub が「この処理は、このリポジトリの main から動いている」という証明書を出し、
# AWS はそれを確かめて、短い時間だけ使える権限を渡す。保存する秘密の値がない。
#
# 渡す権限は、ECR へのイメージの登録と、このサービスの入れ替えだけにしぼる（必要なものだけを渡す）。
# 実際に CD で使う操作は、GitHub Actions の Issue（#49）で確かめ、足りなければ足す。

# GitHub の OIDC プロバイダー（AWS アカウントに1つだけ作れる。このアカウントには、すでにあるので読み取るだけ）
data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

data "aws_caller_identity" "current" {}

resource "aws_iam_role" "github_deploy" {
  name = "${var.app_name}-github-deploy"
  # このロールを使ってよいのは、このリポジトリの main ブランチで動く GitHub Actions だけ
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = data.aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          # このリポジトリは、GitHub の「変わらない番号を使う形（immutable subject）」の設定になっていて、
          # 身分証の sub が「repo:持ち主@番号/名前@番号:ref:…」の形で送られてくる（GitHub の API の
          # actions/oidc/customization/sub で確認）。名前だけの形より安全（名前を変えたり、同じ名前で作り直されたりしても、なりすませない）
          "token.actions.githubusercontent.com:sub" = "${var.github_oidc_subject_prefix}:ref:refs/heads/main"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "github_deploy" {
  name = "deploy"
  role = aws_iam_role.github_deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # ECR にログインする（この操作だけは、リポジトリを指定できない AWS の決まり）
        Sid      = "EcrLogin"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        # このアプリの ECR のリポジトリにだけ、イメージを上げられる
        Sid    = "EcrPush"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:CompleteLayerUpload",
          "ecr:DescribeImages",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
        ]
        Resource = aws_ecr_repository.app.arn
      },
      {
        # このアプリの ECS のサービスを、新しいイメージに入れ替え、状態を確かめられる
        Sid    = "EcsDeploy"
        Effect = "Allow"
        Action = [
          "ecs:DescribeExpressGatewayService",
          "ecs:UpdateExpressGatewayService",
          "ecs:DescribeServices",
          "ecs:DescribeServiceDeployments",
          "ecs:DescribeServiceRevisions",
          "ecs:ListServiceDeployments",
        ]
        Resource = [
          aws_ecs_express_gateway_service.app.service_arn,
          "arn:aws:ecs:${var.region}:${data.aws_caller_identity.current.account_id}:service-deployment/${var.app_name}/*",
          "arn:aws:ecs:${var.region}:${data.aws_caller_identity.current.account_id}:service-revision/${var.app_name}/*",
        ]
      },
      {
        # 入れ替えのとき、ECS が内部で行う操作（AWS の公式の説明「Automated deployments with GitHub Actions for
        # Amazon ECS Express Mode」の権限の一覧のうち、入れ替えに使うもの）。このアプリの箱の設計図・サービス・クラスターだけに限る。
        # 作る操作（CreateCluster、CreateExpressGatewayService）は、Terraform が行うので、CD には渡さない
        Sid    = "EcsDeployInternals"
        Effect = "Allow"
        Action = [
          "ecs:RegisterTaskDefinition",
          "ecs:UpdateService",
          "ecs:DescribeClusters",
        ]
        Resource = [
          "arn:aws:ecs:${var.region}:${data.aws_caller_identity.current.account_id}:task-definition/${var.app_name}-${var.app_name}:*",
          aws_ecs_express_gateway_service.app.service_arn,
          aws_ecs_cluster.main.arn,
        ]
      },
      {
        # サービスを入れ替えるとき、ECS に、実行ロール・タスクロール・インフラロールを渡すための権限（この3つだけ）
        Sid      = "PassEcsRoles"
        Effect   = "Allow"
        Action   = "iam:PassRole"
        Resource = [aws_iam_role.execution.arn, aws_iam_role.task.arn, aws_iam_role.infrastructure.arn]
        Condition = {
          StringEquals = { "iam:PassedToService" = ["ecs-tasks.amazonaws.com", "ecs.amazonaws.com"] }
        }
      },
      {
        # 公開のあとに、ロードバランサーの守りの設定（scripts/harden-alb.sh）が効いているかを読んで確かめる（読むだけ）。
        # ロードバランサーの「読む」操作と、タグで探す操作は、対象をしぼれない AWS の決まりなので "*"
        Sid    = "CheckAlb"
        Effect = "Allow"
        Action = [
          "tag:GetResources",
          "elasticloadbalancing:DescribeLoadBalancerAttributes",
          "elasticloadbalancing:DescribeListeners",
        ]
        Resource = "*"
      },
    ]
  })
}

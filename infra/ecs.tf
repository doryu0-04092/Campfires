# 【役割】アプリの箱を動かす部分（ECS Express Mode）を作るファイル。
# 【なぜ必要か】ECS Express Mode に、イメージと設定を渡すと、Fargate（箱を動かす場所）、
# ロードバランサー（ALB。HTTPS の URL と証明書も）、自動の台数調整、ログをまとめて作ってくれる。
# できあがるものは、ふつうの ECS の部品と同じなので、AWS の画面で中身を確かめたり、細かく変えたりできる。

# 箱をまとめて動かす単位（クラスター）。Fargate（サーバーの管理がいらない、箱を動かす場所）を使う
resource "aws_ecs_cluster" "main" {
  name = var.app_name
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = ["FARGATE"]
}

# アプリのログの置き場所。14日たったログは消す（置いておく分だけ料金がかかるため）
resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.app_name}"
  retention_in_days = 30
}

resource "aws_ecs_express_gateway_service" "app" {
  service_name = var.app_name
  cluster      = aws_ecs_cluster.main.name

  execution_role_arn = aws_iam_role.execution.arn
  # 箱の中のアプリが使う権限（トレースを X-Ray に送る。iam.tf）
  task_role_arn           = aws_iam_role.task.arn
  infrastructure_role_arn = aws_iam_role.infrastructure.arn

  # 箱1つ分の大きさ：0.5 vCPU、メモリ 1GB（Java のアプリが動く、いちばん小さい大きさ）
  cpu    = "512"
  memory = "1024"

  # ロードバランサーが、アプリが動いているかを確かめる URL（HealthController）
  health_check_path = "/api/health"

  primary_container {
    image          = "${aws_ecr_repository.app.repository_url}:${var.image_tag}"
    container_port = 8080

    # ---------- アプリに渡す設定（環境変数） ----------
    # 本番の設定（application-prod.properties）を使う。Dockerfile でも指定しているが、ここでも明示する
    environment {
      name  = "SPRING_PROFILES_ACTIVE"
      value = "prod"
    }
    environment {
      name  = "DB_HOST"
      value = aws_db_instance.main.address
    }
    environment {
      name  = "DB_PORT"
      value = tostring(aws_db_instance.main.port)
    }
    environment {
      name  = "DB_NAME"
      value = aws_db_instance.main.db_name
    }

    # ---------- トレース（docs/observability.md、tracing.tf） ----------
    # Dockerfile では「送らない」にしてあるので、ここで X-Ray の受け口（OTLP）に送るように変える
    environment {
      name  = "OTEL_TRACES_EXPORTER"
      value = "otlp"
    }
    environment {
      name  = "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"
      value = "https://xray.${var.region}.amazonaws.com/v1/traces"
    }
    environment {
      name  = "OTEL_EXPORTER_OTLP_TRACES_PROTOCOL"
      value = "http/protobuf"
    }
    # どのくらいの割合のリクエストを記録するか（variables.tf の trace_sampling_ratio）。
    # traceidratio は、外から送られてきたヘッダーに左右されずに、この割合で決める
    environment {
      name  = "OTEL_TRACES_SAMPLER"
      value = "traceidratio"
    }
    environment {
      name  = "OTEL_TRACES_SAMPLER_ARG"
      value = tostring(var.trace_sampling_ratio)
    }
    # トレースの番号を受け取るヘッダーは、W3C の決まり（traceparent）だけにする。
    # ロードバランサーが付ける X-Amzn-Trace-Id は使わない（docs/observability.md）
    environment {
      name  = "OTEL_PROPAGATORS"
      value = "tracecontext"
    }
    # 本番であることと、ログの置き場所（トレースとログを結び付けるため）
    environment {
      name  = "OTEL_RESOURCE_ATTRIBUTES"
      value = "deployment.environment=production,aws.log.group.names=${aws_cloudwatch_log_group.app.name}"
    }
    # Application Signals（有料の追加の機能）は使わない（AWS の説明で、Transaction Search と料金が重なるため）
    environment {
      name  = "OTEL_AWS_APPLICATION_SIGNALS_ENABLED"
      value = "false"
    }

    # ---------- 秘密の値（Secrets Manager から、ECS が箱に渡す） ----------
    # Secrets Manager に保存したパスワードの JSON（{"username": …, "password": …}。rds.tf）から、項目を1つずつ取り出す。
    # 「ARN:項目の名前::」は、Secrets Manager の JSON の中の1つの項目を指す書き方
    secret {
      name       = "DB_USERNAME"
      value_from = "${aws_secretsmanager_secret.db.arn}:username::"
    }
    secret {
      name       = "DB_PASSWORD"
      value_from = "${aws_secretsmanager_secret.db.arn}:password::"
    }

    aws_logs_configuration {
      log_group         = aws_cloudwatch_log_group.app.name
      log_stream_prefix = "app"
    }
  }

  # 箱を置くサブネット（既定の VPC の公開サブネット）。セキュリティグループは指定しない：
  # Express Mode が、箱用とロードバランサー用を作り、ロードバランサーから箱への通信の許可も設定する（network.tf の説明を参照）
  network_configuration {
    subnets = data.aws_subnets.default.ids
  }

  # 自動の台数調整：ふだんは1つ。CPU の使用率が 70% を超えたら増やす（最大 2つ。費用を抑えるため）
  scaling_target {
    auto_scaling_metric       = "AVERAGE_CPU"
    auto_scaling_target_value = 70
    min_task_count            = 1
    max_task_count            = 2
  }

  # 作り終わって、箱が正しく動き出すまで待つ
  wait_for_steady_state = true

  lifecycle {
    # 2回目からは、CD（GitHub Actions）が新しいイメージに入れ替える。
    # Terraform が、最初のイメージに戻してしまわないように、イメージの変更を無視する
    ignore_changes = [primary_container[0].image]
  }

  # 権限を付け終わってから作る（先に作ると、権限が足りずに失敗する。消すときは、この逆の順番になる）
  depends_on = [
    aws_iam_role_policy_attachment.execution_managed,
    aws_iam_role_policy.execution_db_secret,
    aws_secretsmanager_secret_version.db,
    aws_iam_role_policy_attachment.infrastructure_managed,
    aws_iam_role_policy_attachment.task_xray,
    aws_xray_trace_segment_destination.main,
    aws_ecs_cluster_capacity_providers.main,
  ]
}

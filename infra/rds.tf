# 【役割】データベース（RDS for PostgreSQL）と、そのパスワードの置き場所（Secrets Manager）を作るファイル。
# 【なぜ必要か】本番のデータ（ユーザー、コミュニティ、イベントなど）を、AWS が管理するデータベースに保存するため。
# バックアップ、ソフトの更新、故障したときの入れ替えは、AWS が行う。
#
# パスワードの扱い（セキュリティの見直しで変えた）：
# RDS にパスワードを管理させる仕組み（manage_master_user_password）は、7日ごとにパスワードを自動で変える。
# 一方、ECS は箱を起動したときにしかパスワードを渡さないので、変わった後は新しくデータベースにつなげなくなり、アプリが止まる
# （どちらも AWS の説明に書かれている動き）。そこで、パスワードは Terraform で作り、自動では変えない。
# - パスワードは ephemeral（その場でだけ使い、記録に残さない値）として作る
# - データベースと Secrets Manager には、write-only（書き込むだけで、記録に残さない項目）で渡す
#   → パスワードは、ファイルにも、Terraform の記録（state）にも残らない
# - 変えたいときは、password_version を 1 増やして terraform apply し、ECS の箱を入れ替える（infra/README.md）

# パスワードを作り直す合図。1 増やすと、新しいパスワードを作って、データベースと Secrets Manager に書き込む。
# 注意：ephemeral のパスワードは、Terraform を実行するたびに作り直される。データベースと Secrets Manager は、
# 同じ1回の実行の中で書き込まれたときだけ、同じ値になる。実行が途中で止まり、片方だけが別の回に作られると、値がずれる
# （アプリがデータベースにつなげない：password authentication failed）。そのときも、ここを 1 増やせば、両方がそろう。
# 2：最初の公開で、無料プランの制限でデータベースだけが別の回に作られ、値がずれたため、そろえ直した（2026年10月）
locals {
  password_version = 2
}

# ランダムなパスワード（32文字。記号は、接続の文字列で問題になりにくいものだけを使う）
ephemeral "random_password" "db" {
  length           = 32
  special          = true
  override_special = "-_.~"
}

# ---------- パスワードの置き場所（Secrets Manager） ----------
resource "aws_secretsmanager_secret" "db" {
  name        = "${var.app_name}/db"
  description = "Campfires database credentials (username and password)"
  # terraform destroy のあと、すぐに同じ名前で作り直せるように、消すときは待たずに消す（ポートフォリオなので）
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  # JSON で、利用者の名前とパスワードを保存する（ecs.tf で、項目ごとに取り出して箱に渡す）
  secret_string_wo = jsonencode({
    username = var.app_name
    password = ephemeral.random_password.db.result
  })
  secret_string_wo_version = local.password_version
}

# ---------- データベースの設定（パラメーターグループ） ----------
resource "aws_db_parameter_group" "main" {
  name   = var.app_name
  family = "postgres17"

  # 暗号化していない接続を断る（PostgreSQL 17 の既定も同じだが、設定として明示し、変わらないようにする）
  parameter {
    name  = "rds.force_ssl"
    value = "1"
    # 反映のしかた。AWS は、この設定を「再起動のときに反映（pending-reboot）」として記録するので、同じにする
    # （書かないと、Terraform の既定の immediate と食い違い、毎回「変わった」と表示される。値は同じ 1 で、作るときから効く）
    apply_method = "pending-reboot"
  }
}

# データベースを置くサブネット（既定の VPC のサブネット）
resource "aws_db_subnet_group" "main" {
  name       = var.app_name
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_db_instance" "main" {
  identifier     = var.app_name
  engine         = "postgres"
  engine_version = "17"
  # いちばん小さい大きさ（2 vCPU の一部、メモリ 1GB）。ポートフォリオの利用者数なら十分
  instance_class       = "db.t4g.micro"
  parameter_group_name = aws_db_parameter_group.main.name

  # 保存する場所の大きさ（GB）と種類。中身は暗号化する
  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  # 最初に作るデータベースの名前と、管理者の名前。パスワードは write-only で渡す（記録に残さない）
  db_name             = var.app_name
  username            = var.app_name
  password_wo         = ephemeral.random_password.db.result
  password_wo_version = local.password_version

  # ネットワーク：インターネットからはつながらない。VPC の中からだけつながる（network.tf）
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  # 1台だけ（Multi-AZ にしない）。費用を抑えるため。故障したときは、AWS が入れ替えるまで止まる
  multi_az = false

  # バックアップを残す日数（variables.tf の db_backup_retention_days。ふつうは7日。データベースの大きさまでは、追加の料金なし）
  backup_retention_period = var.db_backup_retention_days
  # ソフトの小さな更新（マイナーバージョン）は、自動で行う（脆弱性の修正を取り込むため）
  auto_minor_version_upgrade = true
  # 設定の変更は、すぐに反映する
  apply_immediately = true

  # terraform destroy で消せるようにする（ポートフォリオなので、消しやすさを優先する。docs/security.md）。
  # 本番で大事なデータを扱うなら、deletion_protection = true にして、消すときに最後のバックアップを取る
  deletion_protection = false
  skip_final_snapshot = true
}

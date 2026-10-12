# 【役割】ネットワーク（VPC、サブネット）と、データベースへの通信の許可（セキュリティグループ）を決めるファイル。
# 【なぜ必要か】アプリの箱とデータベースが、どこに置かれ、だれとつながってよいかを決めるため。
#
# AWS アカウントに最初からある「既定の VPC」を使う。既定の VPC のサブネットは公開サブネットなので、
# ECS Express Mode が箱にパブリック IP を付け、箱が ECR（イメージ）や Secrets Manager（パスワード）に直接つながれる。
# NAT ゲートウェイ（高い）がいらない（AWS の説明：公開サブネットを指定すると assignPublicIp が有効になる）。
#
# 箱とロードバランサーのセキュリティグループは、ECS Express Mode に作らせる（ecs.tf で指定しない）。
# Express Mode が作ったものは、ロードバランサーから箱への通信の許可も、Express Mode が自動で設定する（AWS の説明に書かれた動き）。
# 自分で作ったセキュリティグループを箱に渡すと、どう扱われるかは AWS の説明に書かれていないので、使わない。

# 既定の VPC（data は「すでにあるものを読み取る」書き方。新しくは作らない）
data "aws_vpc" "default" {
  default = true
}

# 既定の VPC の、アベイラビリティゾーン（データセンターのまとまり）ごとの既定のサブネット
data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
  filter {
    name   = "default-for-az"
    values = ["true"]
  }
}

# ---------- データベースのセキュリティグループ ----------
# データベースは、インターネットからはつながらない（rds.tf の publicly_accessible = false）。
# そのうえで、VPC の中からの PostgreSQL の通信（5432）だけを許す。
# 箱のセキュリティグループは Express Mode が作るので、Terraform を書く時点では名前（ID）が分からない。
# そのため、箱を名指しするのではなく、「VPC の中から」という範囲で許す（この VPC には、このアプリしか置かない）
resource "aws_security_group" "db" {
  name        = "${var.app_name}-db"
  description = "Campfires PostgreSQL"
  vpc_id      = data.aws_vpc.default.id
}

resource "aws_vpc_security_group_ingress_rule" "db_from_vpc" {
  security_group_id = aws_security_group.db.id
  ip_protocol       = "tcp"
  from_port         = 5432
  to_port           = 5432
  cidr_ipv4         = data.aws_vpc.default.cidr_block
  description       = "PostgreSQL from inside the VPC only"
}

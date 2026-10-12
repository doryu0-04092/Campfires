# 【役割】Terraform と、AWS の部品を作るためのプロバイダー（AWS Provider）の版を決めるファイル。
# 【なぜ必要か】版が変わると、書き方や動きが変わることがある。使う版を決めておき、だれが動かしても同じ結果にする。
# 実際に使った版は .terraform.lock.hcl に記録され、リポジトリに入れる。

terraform {
  # Terraform 本体の版
  # 1.11 以上：パスワードを記録に残さない write-only の項目を使うため（rds.tf）
  required_version = ">= 1.11"

  # Terraform の記録（tfstate）を、S3 に置く（scripts/create-state-bucket.sh で作ったバケット）。
  # バケットの名前は backend.hcl（Git に入れない）から読む：terraform init -backend-config=backend.hcl
  # use_lockfile：S3 の中に「使用中」の印のファイルを置き、2人が同時に apply して記録を壊さないようにする（Terraform 1.10 から）
  backend "s3" {
    key          = "campfires/terraform.tfstate"
    region       = "ap-northeast-1"
    encrypt      = true
    use_lockfile = true
  }

  required_providers {
    aws = {
      source = "hashicorp/aws"
      # ECS Express Mode（aws_ecs_express_gateway_service）を使える版
      version = "~> 6.0"
    }
    # ランダムなパスワードを作る部品（ephemeral の random_password は 3.7 以上）
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }
}

# AWS のどのリージョン（地域）に作るか。すべての部品に、共通のタグ（目印）を付ける
provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.app_name
      ManagedBy = "terraform"
    }
  }
}

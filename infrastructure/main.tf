terraform {
  backend "s3" {
    bucket = "traccar-infrastructure"
    region = "eu-central-1"
    key = "state.tf"
  }
  required_providers {
    aws = {
      source = "hashicorp/aws"
      version = "~> 4.0"
    }
    random = {
      source = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

provider "aws" {
  region = "eu-central-1"
}

provider "random" {
}

resource "aws_lightsail_key_pair" "ligtsail_key" {
  name = "LightsailKey"
}

resource "aws_lightsail_instance" "traccar_server" {
  count = 1
  availability_zone = "eu-central-1a"
  blueprint_id      = "ubuntu_20_04"
  bundle_id         = "micro_2_0"
  name              = format("TraccarServer%02s", count.index)
  key_pair_name = aws_lightsail_key_pair.ligtsail_key.name
}

resource "random_password" "traccar_database_password" {
  length = 128
  special = false
}

resource "aws_secretsmanager_secret" "traccar_database_credentials" {
  name = "TraccarDatabaseCredentials"
  description = "Secret to store the database credentials for traccar server"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "traccar_database_credentials_v0" {
  secret_id = aws_secretsmanager_secret.traccar_database_credentials.id
  secret_string = jsonencode({
    username = "postgres"
    password = random_password.traccar_database_password.result
  })
}

resource "random_pet" "final_database_snapshot_name" {
}

resource "aws_lightsail_database" "traccar_database" {
  relational_database_name = "TraccarApplicationDatabase"
  availability_zone = "eu-central-1a"
  master_database_name = "application"
  master_username = "postgres"
  master_password = random_password.traccar_database_password.result
  blueprint_id = "postgres_12"
  bundle_id = "micro_2_0"
  publicly_accessible = false
  skip_final_snapshot = false
  final_snapshot_name = random_pet.final_database_snapshot_name.id
}
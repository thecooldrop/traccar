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
    local = {
      source = "hashicorp/local"
      version = "2.2.3"
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

resource "local_sensitive_file" "lightsail_private_key" {
  content = aws_lightsail_key_pair.ligtsail_key.private_key
  filename = "/home/vanio/LightsailPrivateKey.pem"
  file_permission = "0600"
}

resource "local_file" "ansible_inventory" {
  content = yamlencode({
    traccar-servers = {
      hosts = {
        for server in aws_lightsail_instance.traccar_server: server.name => {
          ansible_host = server.public_ip_address
        }
      }
    }
  })
  filename = "${path.module}/inventory.yaml"
}

data "aws_secretsmanager_secret" "dockerhub_credentials" {
  name = "DockerhubCredentials"
}

data "aws_secretsmanager_secret_version" "dockerhub_credentials" {
  secret_id = data.aws_secretsmanager_secret.dockerhub_credentials.id
}

resource "local_sensitive_file" "traccar_servers_docker_credentials" {
  content = yamlencode({
    docker_username = jsondecode(data.aws_secretsmanager_secret_version.dockerhub_credentials.secret_string)["username"]
    docker_password = jsondecode(data.aws_secretsmanager_secret_version.dockerhub_credentials.secret_string)["password"]
  })
  filename = "${path.module}/group_vars/traccar-servers/secret_docker_credentials.yaml"
}

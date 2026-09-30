#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

if [[ -z "${DOCKER_HOST:-}" && -S "$HOME/.docker/desktop/docker.sock" ]]; then
  export DOCKER_HOST="unix://$HOME/.docker/desktop/docker.sock"
fi

docker info >/dev/null
mvn -B -f tacocloud/pom.xml -pl tacocloud,tacocloud-kitchen -am clean verify -Pintegration
python3 scripts/verificar-reportes.py

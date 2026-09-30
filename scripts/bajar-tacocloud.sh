#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
systemctl --user stop tacocloud-local.service 2>/dev/null || true
if [[ -f "$PROJECT_DIR/.runtime/lab.env" ]]; then
  docker compose --env-file "$PROJECT_DIR/.runtime/lab.env" -f "$PROJECT_DIR/compose.yaml" stop
fi
echo "TacoCloud, MongoDB y RabbitMQ detenidos. Los volumenes y datos se conservan."

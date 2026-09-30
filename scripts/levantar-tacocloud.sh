#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$PROJECT_DIR/.runtime"
APP_JAR="$PROJECT_DIR/tacocloud/tacocloud/target/tacocloud-0.0.17-SNAPSHOT.jar"
APP_UNIT="tacocloud-local.service"
APP_PORT="${APP_PORT:-8080}"
mkdir -p "$RUNTIME_DIR"
if ! docker info >/dev/null 2>&1; then
  systemctl --user start docker-desktop
  for attempt in $(seq 1 30); do
    if docker info >/dev/null 2>&1; then break; fi
    sleep 1
  done
fi
docker info >/dev/null
if [[ ! -f "$RUNTIME_DIR/lab.env" ]]; then
  umask 077
  {
    echo "TACO_LAB_PASSWORD=$(openssl rand -hex 16)"
    echo "RABBIT_USER=tacolab"
    echo "RABBIT_PASSWORD=$(openssl rand -hex 24)"
  } > "$RUNTIME_DIR/lab.env"
fi
set -a
source "$RUNTIME_DIR/lab.env"
set +a
docker compose --env-file "$RUNTIME_DIR/lab.env" -f "$PROJECT_DIR/compose.yaml" up -d --wait
if systemctl --user is-active --quiet "$APP_UNIT"; then
  echo "TacoCloud ya esta activo. Para aplicar cambios: bajar y levantar."
  echo "http://localhost:$APP_PORT/ui/home"
  exit 0
fi
if ss -ltn | awk '{print $4}' | grep -Eq "(^|:)$APP_PORT$"; then
  echo "ERROR: el puerto $APP_PORT esta ocupado. Baja el proceso anterior o usa APP_PORT=8082." >&2
  exit 1
fi
if [[ ! -f "$APP_JAR" ]] || find "$PROJECT_DIR/tacocloud" \
  \( -name target -o -name node_modules -o -name dist -o -name node \) -prune -o \
  -type f \( -name '*.java' -o -name '*.ts' -o -name '*.html' -o -name '*.css' -o -name '*.yml' -o -name '*.yaml' -o -name 'pom.xml' \) \
  -newer "$APP_JAR" -print -quit | grep -q .; then
  mvn -B -f "$PROJECT_DIR/tacocloud/pom.xml" -pl tacocloud -am package -DskipTests
fi
systemctl --user reset-failed "$APP_UNIT" >/dev/null 2>&1 || true
systemd-run --user --unit="$APP_UNIT" --collect \
  --property="WorkingDirectory=$PROJECT_DIR" \
  --property="EnvironmentFile=$RUNTIME_DIR/lab.env" \
  --setenv="MONGODB_URI=mongodb://127.0.0.1:27036/tacocloud36?replicaSet=tc36&directConnection=true" \
  --setenv=SPRING_DATA_MONGODB_AUTO_INDEX_CREATION=false \
  --setenv=SPRING_PROFILES_ACTIVE=dev \
  --setenv=TACO_TRANSPORT=rabbit --setenv=RABBIT_HOST=127.0.0.1 --setenv=RABBIT_PORT=5678 \
  --setenv="SERVER_PORT=$APP_PORT" \
  /bin/bash -c 'exec java -jar "$1" >>"$2" 2>&1' _ "$APP_JAR" "$RUNTIME_DIR/tacocloud.log" >/dev/null
for attempt in $(seq 1 60); do
  if curl -fsS --max-time 2 "http://127.0.0.1:$APP_PORT/actuator/health/readiness" >/dev/null; then
    echo "TacoCloud listo: http://localhost:$APP_PORT/ui/home"
    echo "Cocina: http://localhost:$APP_PORT/kitchen.html (inicia sesion como kitchen)"
    echo "Cuentas de laboratorio: admin y kitchen. Contrasena en .runtime/lab.env, variable TACO_LAB_PASSWORD."
    echo "MongoDB: 27036; RabbitMQ: 5678; panel RabbitMQ: http://localhost:15678"
    echo "Log: $RUNTIME_DIR/tacocloud.log"
    exit 0
  fi
  if ! systemctl --user is-active --quiet "$APP_UNIT"; then
    echo "ERROR: revisa $RUNTIME_DIR/tacocloud.log" >&2
    exit 1
  fi
  sleep 1
done
echo "ERROR: la aplicacion no paso readiness. Revisa $RUNTIME_DIR/tacocloud.log" >&2
exit 1

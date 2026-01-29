#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: deploy-bluegreen-local.sh --backend-image IMAGE --frontend-image IMAGE [options]

Options:
  --compose-file PATH        Compose file (default: infra/docker-compose.bluegreen.yml)
  --health-url URL           Healthcheck URL (default: http://localhost/api/actuator/health)
  --health-retries N         Healthcheck retries (default: 12)
  --health-interval SEC      Healthcheck interval seconds (default: 5)
  --active-file PATH         Active color file (default: infra/.active-color)
  --dynamic-dir PATH         Traefik dynamic dir (default: infra/traefik)
EOF
}

BACKEND_IMAGE=""
FRONTEND_IMAGE=""
COMPOSE_FILE="infra/docker-compose.bluegreen.yml"
HEALTH_URL="http://localhost/api/actuator/health"
HEALTH_RETRIES="12"
HEALTH_INTERVAL="5"
ACTIVE_FILE="infra/.active-color"
DYNAMIC_DIR="infra/traefik"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backend-image) BACKEND_IMAGE="$2"; shift 2 ;;
    --frontend-image) FRONTEND_IMAGE="$2"; shift 2 ;;
    --compose-file) COMPOSE_FILE="$2"; shift 2 ;;
    --health-url) HEALTH_URL="$2"; shift 2 ;;
    --health-retries) HEALTH_RETRIES="$2"; shift 2 ;;
    --health-interval) HEALTH_INTERVAL="$2"; shift 2 ;;
    --active-file) ACTIVE_FILE="$2"; shift 2 ;;
    --dynamic-dir) DYNAMIC_DIR="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 1 ;;
  esac
done

if [[ -z "$BACKEND_IMAGE" || -z "$FRONTEND_IMAGE" ]]; then
  echo "backend/frontend image required"
  usage
  exit 1
fi

ACTIVE="blue"
if [[ -f "$ACTIVE_FILE" ]]; then
  ACTIVE=$(cat "$ACTIVE_FILE" | tr -d '\n' || echo "blue")
fi
if [[ "$ACTIVE" != "blue" && "$ACTIVE" != "green" ]]; then
  ACTIVE="blue"
fi
if [[ "$ACTIVE" == "blue" ]]; then
  NEXT="green"
else
  NEXT="blue"
fi

docker tag "$BACKEND_IMAGE" "btc-backend:$NEXT"
docker tag "$FRONTEND_IMAGE" "btc-frontend:$NEXT"

docker compose -f "$COMPOSE_FILE" up -d traefik
docker compose -f "$COMPOSE_FILE" up -d "backend-$NEXT" "frontend-$NEXT"

echo "Waiting for healthcheck $HEALTH_URL"
ok=0
for ((i=1; i<=HEALTH_RETRIES; i++)); do
  if curl -fsS "$HEALTH_URL" >/dev/null; then
    ok=1
    break
  fi
  sleep "$HEALTH_INTERVAL"
done

if [[ "$ok" -ne 1 ]]; then
  echo "Healthcheck failed, keeping $ACTIVE"
  exit 1
fi

cp "$DYNAMIC_DIR/dynamic.$NEXT.yml" "$DYNAMIC_DIR/dynamic.yml"
echo "$NEXT" > "$ACTIVE_FILE"

docker compose -f "$COMPOSE_FILE" stop "backend-$ACTIVE" "frontend-$ACTIVE"

echo "Switched active color to $NEXT"

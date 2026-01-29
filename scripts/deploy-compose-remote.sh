#!/usr/bin/env bash
set -euo pipefail

HOST=""
USER="ubuntu"
PORT="22"
REMOTE_DIR="/opt/btc-auto-trader"
ENV_FILE="infra/.env.prod"
COMPOSE_FILE="infra/docker-compose.prod.registry.yml"
BACKEND_IMAGE=""
FRONTEND_IMAGE=""
COPY_ENV="false"
HEALTH_URL="http://localhost/api/actuator/health"
FRONTEND_URL="http://localhost/"
HEALTH_RETRIES=12
HEALTH_INTERVAL=5
ROLLBACK_ON_FAIL="false"
DOCKER_CMD="docker"
COMPOSE_PROJECT="btc-trader"
SKIP_SYNC="false"

usage() {
  cat <<EOF
Usage: deploy-compose-remote.sh --host <host> [options]

Options:
  --user <user>              SSH user (default: ubuntu)
  --port <port>              SSH port (default: 22)
  --dir <path>               Remote app dir (default: /opt/btc-auto-trader)
  --env-file <path>          Local env file (default: infra/.env.prod)
  --compose-file <path>      Local compose file (default: infra/docker-compose.prod.registry.yml)
  --backend-image <image>    Backend image (optional)
  --frontend-image <image>   Frontend image (optional)
  --copy-env                 Copy env file to remote (default: false)
  --health-url <url>         Backend health URL (default: http://localhost/api/actuator/health)
  --frontend-url <url>       Frontend URL (default: http://localhost/)
  --health-retries <n>       Health retries (default: 12)
  --health-interval <sec>    Health interval seconds (default: 5)
  --rollback-on-fail         Roll back on failed healthcheck
  --docker-cmd <cmd>          Remote docker command (default: docker)
  --compose-project <name>    Compose project name (default: btc-trader)
  --skip-sync                 Skip syncing infra files

Example:
  ./scripts/deploy-compose-remote.sh --host 1.2.3.4 \
    --backend-image ghcr.io/you/btc-backend:latest \
    --frontend-image ghcr.io/you/btc-frontend:latest
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) HOST="$2"; shift 2 ;;
    --user) USER="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    --dir) REMOTE_DIR="$2"; shift 2 ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --compose-file) COMPOSE_FILE="$2"; shift 2 ;;
    --backend-image) BACKEND_IMAGE="$2"; shift 2 ;;
    --frontend-image) FRONTEND_IMAGE="$2"; shift 2 ;;
    --copy-env) COPY_ENV="true"; shift 1 ;;
    --health-url) HEALTH_URL="$2"; shift 2 ;;
    --frontend-url) FRONTEND_URL="$2"; shift 2 ;;
    --health-retries) HEALTH_RETRIES="$2"; shift 2 ;;
    --health-interval) HEALTH_INTERVAL="$2"; shift 2 ;;
    --rollback-on-fail) ROLLBACK_ON_FAIL="true"; shift 1 ;;
    --docker-cmd) DOCKER_CMD="$2"; shift 2 ;;
    --compose-project) COMPOSE_PROJECT="$2"; shift 2 ;;
    --skip-sync) SKIP_SYNC="true"; shift 1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 1 ;;
  esac
 done

if [[ -z "$HOST" ]]; then
  echo "--host is required"
  usage
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Compose file not found: $COMPOSE_FILE"
  exit 1
fi

REMOTE="${USER}@${HOST}"
PORT_OPT="-p ${PORT}"

ssh ${PORT_OPT} ${REMOTE} "mkdir -p ${REMOTE_DIR}/infra"

if [[ "$SKIP_SYNC" != "true" ]]; then
  if command -v rsync >/dev/null 2>&1; then
    rsync -av --delete --exclude '.env.prod' infra/ ${REMOTE}:${REMOTE_DIR}/infra/
  else
    scp ${PORT_OPT} infra/Caddyfile ${REMOTE}:${REMOTE_DIR}/infra/
    scp ${PORT_OPT} infra/docker-compose.prod.yml ${REMOTE}:${REMOTE_DIR}/infra/
    scp ${PORT_OPT} infra/docker-compose.prod.registry.yml ${REMOTE}:${REMOTE_DIR}/infra/
    scp ${PORT_OPT} infra/.env.prod.example ${REMOTE}:${REMOTE_DIR}/infra/
  fi
fi

if [[ "$COPY_ENV" == "true" ]]; then
  scp ${PORT_OPT} ${ENV_FILE} ${REMOTE}:${REMOTE_DIR}/infra/.env.prod
fi

# store previous images on remote for rollback
ssh ${PORT_OPT} ${REMOTE} "bash -lc 'cd ${REMOTE_DIR}/infra && export COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT}; backend_id=$(${DOCKER_CMD} compose -f docker-compose.prod.registry.yml --env-file .env.prod ps -q backend || true); frontend_id=$(${DOCKER_CMD} compose -f docker-compose.prod.registry.yml --env-file .env.prod ps -q frontend || true); backend_img=""; frontend_img=""; if [ -n "$backend_id" ]; then backend_img=$(${DOCKER_CMD} inspect -f "{{.Config.Image}}" "$backend_id"); fi; if [ -n "$frontend_id" ]; then frontend_img=$(${DOCKER_CMD} inspect -f "{{.Config.Image}}" "$frontend_id"); fi; printf "BACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n" "$backend_img" "$frontend_img" > .last_images'"

REMOTE_CMD="cd ${REMOTE_DIR}/infra && export COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT}"
if [[ -n "$BACKEND_IMAGE" ]]; then
  REMOTE_CMD+=" && export BACKEND_IMAGE=${BACKEND_IMAGE}"
fi
if [[ -n "$FRONTEND_IMAGE" ]]; then
  REMOTE_CMD+=" && export FRONTEND_IMAGE=${FRONTEND_IMAGE}"
fi
REMOTE_CMD+=" && ${DOCKER_CMD} compose -f docker-compose.prod.registry.yml --env-file .env.prod up -d"

ssh ${PORT_OPT} ${REMOTE} "${REMOTE_CMD}"

# healthcheck
HEALTH_CMD="attempts=0; while [ $attempts -lt ${HEALTH_RETRIES} ]; do if curl -fsS '${HEALTH_URL}' >/dev/null 2>&1 && curl -fsS '${FRONTEND_URL}' >/dev/null 2>&1; then exit 0; fi; attempts=$((attempts+1)); sleep ${HEALTH_INTERVAL}; done; exit 1"
if ! ssh ${PORT_OPT} ${REMOTE} "bash -lc '${HEALTH_CMD}'"; then
  echo "Healthcheck failed."
  if [[ "$ROLLBACK_ON_FAIL" == "true" ]]; then
    echo "Rolling back using previous images."
    ssh ${PORT_OPT} ${REMOTE} "bash -lc 'cd ${REMOTE_DIR}/infra && export COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT}; if [ -f .last_images ]; then set -a; source .last_images; set +a; fi; if [ -n "$BACKEND_IMAGE" ] || [ -n "$FRONTEND_IMAGE" ]; then ${DOCKER_CMD} compose -f docker-compose.prod.registry.yml --env-file .env.prod up -d; fi'"
  fi
  exit 1
fi

echo "Deploy complete."

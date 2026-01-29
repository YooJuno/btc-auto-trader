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

if command -v rsync >/dev/null 2>&1; then
  rsync -av --delete --exclude '.env.prod' infra/ ${REMOTE}:${REMOTE_DIR}/infra/
else
  scp ${PORT_OPT} infra/Caddyfile ${REMOTE}:${REMOTE_DIR}/infra/
  scp ${PORT_OPT} infra/docker-compose.prod.yml ${REMOTE}:${REMOTE_DIR}/infra/
  scp ${PORT_OPT} infra/docker-compose.prod.registry.yml ${REMOTE}:${REMOTE_DIR}/infra/
  scp ${PORT_OPT} infra/.env.prod.example ${REMOTE}:${REMOTE_DIR}/infra/
fi

if [[ "$COPY_ENV" == "true" ]]; then
  scp ${PORT_OPT} ${ENV_FILE} ${REMOTE}:${REMOTE_DIR}/infra/.env.prod
fi

export BACKEND_IMAGE FRONTEND_IMAGE
REMOTE_CMD="cd ${REMOTE_DIR}/infra"
if [[ -n "$BACKEND_IMAGE" ]]; then
  REMOTE_CMD+=" && export BACKEND_IMAGE=${BACKEND_IMAGE}"
fi
if [[ -n "$FRONTEND_IMAGE" ]]; then
  REMOTE_CMD+=" && export FRONTEND_IMAGE=${FRONTEND_IMAGE}"
fi
REMOTE_CMD+=" && docker compose -f docker-compose.prod.registry.yml --env-file .env.prod up -d"

ssh ${PORT_OPT} ${REMOTE} "${REMOTE_CMD}"

echo "Deploy complete."

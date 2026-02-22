#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/apps/backend"
FRONTEND_DIR="$ROOT_DIR/apps/frontend"
SYSTEMD_DIR="/etc/systemd/system"
UNIT_CHANGED=0

# 1) Build backend jar
cd "$BACKEND_DIR"
./gradlew bootJar

# 2) Build frontend bundle
cd "$FRONTEND_DIR"
if [ ! -d node_modules ] || [ package-lock.json -nt node_modules ]; then
  npm ci
fi
npm run build

# 3) Copy systemd unit files only if changed
for unit in btc-backend.service btc-frontend.service; do
  src="$ROOT_DIR/scripts/$unit"
  dst="$SYSTEMD_DIR/$unit"

  if ! sudo test -f "$dst" || ! sudo cmp -s "$src" "$dst"; then
    sudo cp "$src" "$dst"
    UNIT_CHANGED=1
  fi
done

# 4) Reload daemon only when unit files changed (before restart)
if [ "$UNIT_CHANGED" -eq 1 ]; then
  sudo systemctl daemon-reload
fi

# 5) Restart services
sudo systemctl restart btc-backend.service btc-frontend.service

#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SYSTEMD_DIR="/etc/systemd/system"

sudo cp "$ROOT_DIR/scripts/btc-backend.service" "$SYSTEMD_DIR/btc-backend.service"
sudo cp "$ROOT_DIR/scripts/btc-frontend.service" "$SYSTEMD_DIR/btc-frontend.service"

sudo systemctl daemon-reload

sudo systemctl enable --now btc-frontend.service
sudo systemctl enable --now btc-backend.service

if [ "${INSTALL_STRATEGY_LAB:-false}" = "true" ]; then
  sudo cp "$ROOT_DIR/scripts/btc-strategy-lab.service" "$SYSTEMD_DIR/btc-strategy-lab.service"
  sudo systemctl daemon-reload
  sudo systemctl enable --now btc-strategy-lab.service
fi

# sudo systemctl status btc-frontend.service btc-backend.service

#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SYSTEMD_DIR="/etc/systemd/system"
UNIT_NAME="btc-strategy-lab.service"

sudo cp "$ROOT_DIR/scripts/$UNIT_NAME" "$SYSTEMD_DIR/$UNIT_NAME"
sudo systemctl daemon-reload
sudo systemctl enable --now "$UNIT_NAME"

sudo systemctl status "$UNIT_NAME" --no-pager -l

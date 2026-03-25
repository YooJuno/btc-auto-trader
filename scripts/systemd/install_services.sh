#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
UNIT_DIR="$ROOT_DIR/scripts/systemd"
SYSTEMD_DIR="/etc/systemd/system"
INSTALL_APP=true
INCLUDE_STRATEGY_LAB=false
UNIT_CHANGED=0

usage() {
  cat <<'EOF'
Usage:
  ./scripts/systemd/install_services.sh
  ./scripts/systemd/install_services.sh --with-strategy-lab
  ./scripts/systemd/install_services.sh --strategy-only
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --with-strategy-lab)
      INCLUDE_STRATEGY_LAB=true
      ;;
    --strategy-only)
      INSTALL_APP=false
      INCLUDE_STRATEGY_LAB=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

units=()
if [ "$INSTALL_APP" = true ]; then
  units+=(btc-backend.service btc-frontend.service)
fi
if [ "$INCLUDE_STRATEGY_LAB" = true ]; then
  units+=(btc-strategy-lab.service)
fi

if [ "${#units[@]}" -eq 0 ]; then
  echo "No systemd units selected." >&2
  exit 1
fi

for unit in "${units[@]}"; do
  src="$UNIT_DIR/$unit"
  dst="$SYSTEMD_DIR/$unit"

  if ! sudo test -f "$dst" || ! sudo cmp -s "$src" "$dst"; then
    sudo cp "$src" "$dst"
    UNIT_CHANGED=1
  fi
done

if [ "$UNIT_CHANGED" -eq 1 ]; then
  sudo systemctl daemon-reload
fi

sudo systemctl enable --now "${units[@]}"
sudo systemctl status "${units[@]}" --no-pager -l

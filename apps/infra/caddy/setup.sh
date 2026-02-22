#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "Usage: $0 <domain> [acme_email]"
  echo "Example: $0 app.yourdomain.com you@example.com"
  echo
  echo "Optional env:"
  echo "  FRONTEND_PORT=5173 BACKEND_PORT=8080 CADDYFILE_PATH=/etc/caddy/Caddyfile"
  exit 1
fi

DOMAIN="$1"
ACME_EMAIL="${2:-}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
CADDYFILE_PATH="${CADDYFILE_PATH:-/etc/caddy/Caddyfile}"
TMP_CONFIG="$(mktemp)"

cleanup() {
  rm -f "$TMP_CONFIG"
}
trap cleanup EXIT

is_valid_port() {
  local value="$1"
  [[ "$value" =~ ^[0-9]+$ ]] && [ "$value" -ge 1 ] && [ "$value" -le 65535 ]
}

if [[ "$DOMAIN" =~ [[:space:]] ]] || [[ "$DOMAIN" != *.* ]]; then
  echo "invalid domain: $DOMAIN"
  exit 1
fi

if ! is_valid_port "$FRONTEND_PORT"; then
  echo "invalid FRONTEND_PORT: $FRONTEND_PORT"
  exit 1
fi

if ! is_valid_port "$BACKEND_PORT"; then
  echo "invalid BACKEND_PORT: $BACKEND_PORT"
  exit 1
fi

if ! command -v caddy >/dev/null 2>&1; then
  echo "caddy is not installed. Install it first:"
  echo "  sudo apt update && sudo apt install -y caddy"
  exit 1
fi

if [ -n "$ACME_EMAIL" ]; then
  cat >"$TMP_CONFIG" <<EOF
{
    email $ACME_EMAIL
}

$DOMAIN {
    encode zstd gzip

    @backend path /api /api/* /oauth2 /oauth2/* /login /login/* /logout /logout/*
    handle @backend {
        reverse_proxy 127.0.0.1:$BACKEND_PORT
    }

    handle {
        reverse_proxy 127.0.0.1:$FRONTEND_PORT
    }
}
EOF
else
  cat >"$TMP_CONFIG" <<EOF
$DOMAIN {
    encode zstd gzip

    @backend path /api /api/* /oauth2 /oauth2/* /login /login/* /logout /logout/*
    handle @backend {
        reverse_proxy 127.0.0.1:$BACKEND_PORT
    }

    handle {
        reverse_proxy 127.0.0.1:$FRONTEND_PORT
    }
}
EOF
fi

if sudo test -f "$CADDYFILE_PATH"; then
  BACKUP_PATH="$CADDYFILE_PATH.backup.$(date +%Y%m%d%H%M%S)"
  sudo cp "$CADDYFILE_PATH" "$BACKUP_PATH"
  echo "backup created: $BACKUP_PATH"
fi

sudo cp "$TMP_CONFIG" "$CADDYFILE_PATH"
sudo caddy validate --config "$CADDYFILE_PATH"
sudo systemctl enable --now caddy.service
sudo systemctl reload caddy.service

echo
echo "Caddy applied."
echo "domain         : $DOMAIN"
echo "frontend_port  : $FRONTEND_PORT"
echo "backend_port   : $BACKEND_PORT"
echo "caddyfile_path : $CADDYFILE_PATH"
echo
sudo systemctl status caddy.service --no-pager -l

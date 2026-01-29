# BTC Auto Trader

KRW-first, multi-coin auto-trading console for Upbit. This repo is a monorepo with a Spring Boot backend and a React frontend.

## Stack
- Backend: Spring Boot (Security, JPA, Validation)
- Frontend: React + Vite + TypeScript
- DB: PostgreSQL

## Quickstart (local)
1) Start Postgres

```bash
cd infra
cp .env.example .env
# edit .env values if needed
docker compose up -d
```

2) Run backend

```bash
cd backend
./gradlew bootRun
```

3) Run frontend

```bash
cd frontend
npm install
npm run dev
```

## Production (Docker Compose)
```bash
cd infra
cp .env.prod.example .env.prod
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Frontend will be exposed on ports 80/443 via Caddy. Backend runs behind `/api`.

## Production (Docker Compose + Registry images)
```bash
cd infra
cp .env.prod.example .env.prod
# set BACKEND_IMAGE / FRONTEND_IMAGE to your registry images
docker compose -f docker-compose.prod.registry.yml --env-file .env.prod up -d
```

## Environment variables
Backend reads:
- `DB_URL` (default: `jdbc:postgresql://localhost:5432/btctrader`)
- `DB_USER` (default: `btctrader`)
- `DB_PASSWORD` (default: `btctrader`)
- `JWT_SECRET` (dev default is set, change for real use)
- `APP_ENC_KEY` (Base64-encoded 32-byte key for API key encryption)
- `UPBIT_STREAM_INTERVAL_MS` (SSE recommendation stream interval)
- `UPBIT_RECOMMENDATION_CACHE_MS` (recommendation cache TTL)

## Notes
- API keys are stored encrypted in the backend (AES-GCM).
- Public Upbit market data is integrated; execution (real trades) is still stubbed.
  - Paper trading mode simulates fills using the strategy engine.
  - Optional Upbit WebSocket stream boosts price freshness when enabled.
  - SSE stream (`/api/market/stream`) feeds the frontend market radar.

## Kubernetes
See `k8s/README.md` for Kustomize overlays and apply steps.

## Strategy
See `docs/STRATEGY.md` for the v1 signal logic.

## Paper trading endpoints
- `GET /api/paper/summary`
- `POST /api/paper/reset`
- `GET /api/paper/performance`

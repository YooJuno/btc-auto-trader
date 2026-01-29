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
chmod +x ./gradlew
# .env가 있으면 로드 (로컬 전용, 커밋 금지)
set -a
source .env
set +a
./gradlew bootRun
```

3) Run frontend

```bash
cd frontend
npm install
npm run dev
```

## Local setup (Linux / macOS / Windows)
공통으로 필요한 것:
- Java 17+ (Spring Boot)
- Node.js 18+ (Frontend)
- Git (권장)
- Docker (Postgres를 컨테이너로 띄울 경우)

아래는 운영체제별 권장 설치 흐름입니다. 정확한 설치는 각 공식 설치 프로그램을 사용하세요.

### Linux (Ubuntu/Debian 기준)
1) Java 17+ 설치 (OpenJDK 권장)
2) Node.js 18+ 설치 (nvm 사용 권장)
3) Docker Engine + Docker Compose 설치
4) `docker compose up -d`로 Postgres 실행 후 백엔드/프론트 실행
   - macOS는 Docker Desktop 앱이 실행 중이어야 합니다.

예시 명령 (Ubuntu/Debian):
```bash
sudo apt update
sudo apt install -y git curl ca-certificates openjdk-17-jdk

# nvm 설치 후 Node 18 LTS
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
export NVM_DIR="$HOME/.nvm"
source "$NVM_DIR/nvm.sh"
nvm install 18
nvm use 18

# Docker + Compose plugin
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo $VERSION_CODENAME) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```

### macOS
1) Java 17+ 설치 (Temurin 또는 OpenJDK 권장)
2) Node.js 18+ 설치 (nvm 또는 공식 installer)
3) Docker Desktop 설치
4) `docker compose up -d`로 Postgres 실행 후 백엔드/프론트 실행

예시 명령 (Homebrew 사용 시):
```bash
brew update
brew install git node openjdk@17
brew install --cask docker
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

### Windows
1) Java 17+ 설치 (Temurin 또는 OpenJDK 권장)
2) Node.js 18+ 설치 (공식 installer)
3) Docker Desktop 설치
4) `docker compose up -d`로 Postgres 실행 후 백엔드/프론트 실행

예시 명령 (winget 사용 시, PowerShell):
```powershell
winget install EclipseAdoptium.Temurin.17.JDK
winget install OpenJS.NodeJS.LTS
winget install Git.Git
winget install Docker.DockerDesktop
```

### 설치 확인 체크리스트
```bash
java -version
node -v
npm -v
git --version
docker --version
docker compose version
```

> Windows에서 Docker 성능이 중요하면 WSL2 사용을 권장합니다.

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
- `UPBIT_ACCESS_KEY` / `UPBIT_SECRET_KEY` (Upbit API keys)
- `UPBIT_STREAM_INTERVAL_MS` (SSE recommendation stream interval)
- `UPBIT_RECOMMENDATION_CACHE_MS` (recommendation cache TTL)
- `UPBIT_FAST_RECOMMEND` (skip candle/indicator calls for faster recommendations)
- `UPBIT_RECO_CANDIDATE_FACTOR` (top-volume candidate multiplier, default 2)
- `UPBIT_RECO_MIN_CANDLES` (min candle count when using indicators)
- `UPBIT_REST_MIN_INTERVAL_MS` (min gap between Upbit REST calls)
- `UPBIT_REST_RETRY_MAX` / `UPBIT_REST_RETRY_BACKOFF_MS` (429 retry controls)
- `UPBIT_MARKET_CACHE_MS` / `UPBIT_CANDLE_CACHE_MS` (Upbit market/candle cache TTL)
- `ENGINE_ENABLED` (paper trading engine on/off)
- `ENGINE_INTERVAL_MS` (paper trading engine interval)

Local secrets
- `backend/.env.example`를 `backend/.env`로 복사 후 키를 입력하세요.
- `.env`는 **절대 커밋하지 마세요**.

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

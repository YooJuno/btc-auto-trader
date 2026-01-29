# BTC Auto Trader

Upbit 기반 KRW 중심 자동매매 콘솔.  
Spring Boot 백엔드 + React 프론트 + PostgreSQL로 구성된 모노레포입니다.

---

## ✅ 핵심 기능
- 실시간 추천(거래대금/추세/변동성 기반)
- 모의계좌(Paper Trading) 포트폴리오/손익 표시
- 자동매매 설정(전략/리스크/선정 방식)
- SSE 기반 스트리밍 업데이트
- 캐시/레이트리밋 완화로 429 대응

---

## 🧭 화면 구조 (UI)
**Dashboard**
- 잔고/Equity, 보유 종목, 주요 차트, 자동매매 요약, 추천 목록

**Holdings**
- 보유 종목 상세(수량/매수금액/손익률/손익금액)

**Automation**
- 전략/리스크/선정 방식 + 추천 시그널

**Settings**
- 자동매매 설정(고급 파라미터 포함)

---

## 🧱 기술 스택
- Backend: Spring Boot (Security, JPA, Validation)
- Frontend: React + Vite + TypeScript
- DB: PostgreSQL
- Infra: Docker Compose, Traefik (Blue/Green), Jenkins

---

## 🚀 빠른 실행 (로컬)

### 1) PostgreSQL 실행
```bash
cd infra
cp .env.example .env
docker compose up -d
```

### 2) 백엔드 실행
```bash
cd backend
chmod +x ./gradlew
set -a
source .env
set +a
./gradlew bootRun
```

### 3) 프론트 실행
```bash
cd frontend
npm install
npm run dev
```

접속: `http://localhost:5173`

---

## 🐳 Docker 로컬 빌드
```bash
docker build -t btc-backend:latest backend
docker build -t btc-frontend:latest frontend
```

---

## 🔁 Blue/Green 무중단 배포 (Traefik + Jenkins)

### 1) Traefik 실행
```bash
docker compose -f infra/docker-compose.bluegreen.yml up -d traefik
```

### 2) 배포 실행
```bash
./scripts/deploy-bluegreen-local.sh --backend-image btc-backend:latest --frontend-image btc-frontend:latest
```

### 3) 포트포워딩
- 공유기에서 **외부 80 → 맥북 80**
- IP로 접속 가능: `http://{공인IP}`

> HTTPS는 도메인/DDNS 없이는 불가. (IP만으로 인증서 발급 불가)

---

## 🤖 Jenkins 자동 배포 흐름
Git push → Jenkins 자동 빌드 → Blue/Green 배포

### Jenkins 파라미터
`DEPLOY_TARGET=local-bluegreen`  
`LOCAL_COMPOSE_FILE=infra/docker-compose.bluegreen.yml`  
`LOCAL_HEALTH_URL=http://localhost/api/actuator/health`

---

## 🔑 환경 변수 (.env)
`backend/.env.example` 참고

핵심:
- `UPBIT_ACCESS_KEY`, `UPBIT_SECRET_KEY`
- `UPBIT_RECOMMENDATION_CACHE_MS`
- `UPBIT_REST_MIN_INTERVAL_MS`
- `ENGINE_ENABLED`, `ENGINE_INTERVAL_MS`

---

## 📡 API 요약
**추천 스트림**
- `GET /api/market/stream?topN=5` (SSE)

**추천 단건**
- `GET /api/market/recommendations?topN=5`

**캔들**
- `GET /api/market/candles?market=KRW-BTC&limit=40`

**모의계좌**
- `GET /api/paper/summary`
- `POST /api/paper/reset`
- `GET /api/paper/performance`

---

## ✅ 참고
Kubernetes 전환은 나중에 가능하도록 구성됨 (`k8s/`).


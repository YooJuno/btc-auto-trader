# BTC Auto Trader

Upbit 기반 KRW 중심 자동매매 콘솔.  
Spring Boot 백엔드 + React 프론트 + PostgreSQL로 구성된 모노레포입니다.

---

## ✅ 핵심 기능
- 추세 돌파 기반 자동매매 엔진 (1시간봉 신호 / 4시간봉 상위 추세 확인)
- 실계좌(Upbit) 포트폴리오·평가손익 표시
- 마켓별 전략/리스크 설정, 마켓 단위 일시정지
- **엔진 판단 로그**: 매 틱의 판단 근거(진입/청산 사유, 미체결 사유, 지표 스냅샷)를 저장하고 화면에 표시
- **긴급 청산**: 엔진 중지 + 보유 코인 전량 시장가 매도 (`POST /api/engine/panic`)

### 모의매매 (Paper Trading)

`trading.mode=PAPER` 로 전환하면 **실제 시세**에 대해 **모의 체결**로 동작합니다.

- 계좌 잔고와 주문 체결만 시뮬레이션합니다. 신호·지표·청산·사이징·레짐 게이트는 실매매와 **동일한 코드
  경로**를 그대로 탑니다. 그래야 모의 결과가 실매매에 대한 근거가 됩니다.
- 체결가는 엔진이 주문 크기를 계산할 때 쓰는 것과 **같은 수수료·슬리피지**로 산정됩니다.
- 평균매수가는 업비트와 동일하게 **수수료 제외** 기준입니다. 손절이 평균매수가와 비교되므로, 여기서
  기준이 달라지면 모의와 실매매의 손절 위치가 어긋납니다.
- 잔고 부족·수량 부족은 조용히 건너뛰지 않고 **거부된 주문으로 기록**됩니다.
- 지정가는 이미 시장가가 통과한 경우에만 체결되고, 미체결 지정가는 거부됩니다(호가창 시뮬레이션 없음).
- 화면 좌측 상단에 `모의` / `실계좌` 배지가 항상 표시됩니다.

```dotenv
trading.mode=PAPER
trading.paper.initial-krw=1000000
```

기본값은 `LIVE` 입니다 — 이 빌드를 배포해도 기존 실계좌 운용이 조용히 멈추지 않도록 하기 위함입니다.

## Backend
- Spring Boot v3.3.4
- Upbit API 연동
- JPA + PostgreSQL
- 스케줄러 기반 자동매매 로직
- port : 8080

## Frontend
- React v22.22.0 + Vite v7.3.1
- port : 5173

## Infrastructure
- PostgreSQL v16.11
- port : 5432

## Authentication (OAuth2)
- 사용자 로그인은 OAuth2(Session) 기반입니다.
- 로그인 후 사용자별 인터페이스 설정은 `user_settings` 테이블에 저장됩니다.
- 프론트는 `/api/me` -> `/api/me/settings` 순서로 사용자 화면을 초기화합니다.

### Required env examples
```dotenv
# Spring OAuth2 client registration (example: Google)
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=your-google-client-id
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=your-google-client-secret
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE=openid,profile,email
# Optional: force fixed callback URL (recommended for public HTTPS domain)
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI=https://your-domain/login/oauth2/code/google

# Frontend redirect after login (path only 권장)
APP_AUTH_SUCCESS_REDIRECT_URL=/
APP_AUTH_FAILURE_REDIRECT_URL=/?loginError=true
APP_AUTH_DYNAMIC_REDIRECT_ENABLED=true
APP_AUTH_FRONTEND_PORT=443 # 로컬 개발 시 5173, HTTPS 공개 운영 시 443

# Tenant owner account
APP_OWNER_EMAIL=juno980220@gmail.com

# Trading safety mode (default: owner account only)
APP_TRADING_OWNER_ONLY_MODE=true

# Exchange credential encryption key (required in production)
APP_EXCHANGE_KEY_ENCRYPTION_KEY=change-this-to-a-long-random-secret
```

### Local dev note
- Vite dev server는 `/api`, `/oauth2`, `/login` 경로를 백엔드(`:8080`)로 프록시합니다.
- OAuth 정상 동작을 위해 Vite 프록시는 `changeOrigin=false`로 원본 Host를 유지해야 합니다.
- `APP_OWNER_EMAIL` 계정은 기존 메인 DB를 사용하고, 신규 로그인 계정은 `btc_user_<user_id>` 형태의 전용 DB를 자동 생성합니다.
- `/api/engine/*`, `/api/order/*`, `/api/strategy/*`, `/api/portfolio/*`는 로그인 사용자 tenant DB 기준으로 동작합니다.
- 거래소 API 키는 사용자별로 암호화 저장되며(`user_exchange_credentials`), `/api/me/exchange-credentials`에서 관리합니다.

### Google OAuth `invalid_request`(정책 위반) 대응
- Google은 `localhost`가 아닌 공개 주소의 OAuth 콜백을 `http://`로 받으면 차단합니다.
- 공개 접속은 반드시 `https://도메인`으로 운영하세요. (IP + HTTP는 차단될 수 있음)
- Google Cloud Console > OAuth 클라이언트에 아래 URI를 정확히 등록해야 합니다.
  - `https://your-domain/login/oauth2/code/google`
- 서버 환경변수도 동일하게 맞추세요.
  - `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI=https://your-domain/login/oauth2/code/google`

### Cloudflare + Caddy 배포(권장)
- 실행 문서: `apps/infra/caddy/README.md`
- 핵심 요약:
  - 외부 포트는 `443` 고정
  - 내부 앱 포트는 그대로 유지 (`frontend:5173`, `backend:8080`)
  - Caddy가 `/api`, `/oauth2`, `/login`, `/logout`은 백엔드로 프록시하고 나머지는 프론트로 전달

### Jenkins CI/CD
- 실행 문서: `apps/infra/jenkins/README.md`
- 파이프라인 파일: `Jenkinsfile`
- 기본 CI: backend test + frontend lint/build + artifact 보관
- 선택 CD: `main` 브랜치에서 `DEPLOY=true`일 때 원격 `./scripts/deploy/deploy_app.sh` 실행

### 백테스트 가이드
- 실행/검증 문서: `docs/BACKTEST_GUIDE.md`
- 기본 실행:
  - `python3 scripts/research/backtest.py --days 30`
- 파라미터 탐색 포함:
  - `python3 scripts/research/backtest.py --days 30 --optimize --max-combos 120`

### Strategy Lab (지속 테스트 루프)
- 실행 문서: `docs/STRATEGY_LAB.md`
- 역할:
  - 실시간 자동매매와 별개로 백테스트를 주기적으로 반복
  - 결과를 `data/strategy-lab/`에 누적 저장
  - 다음 Codex 요청 시 누적 데이터 기반으로 전략 코드/설정 반영
- 설치/시작:
  - `./scripts/systemd/install_services.sh --strategy-only`

### Tenant 분리 확인 체크리스트
1. 계정 A/B 각각 로그인 후 `/api/me`의 `tenantDatabase`가 다른지 확인
2. A에서 `/api/strategy/markets` 변경 후 B에서 조회했을 때 값이 분리되는지 확인
3. A에서 주문 생성 후 B의 `/api/order/history`에 보이지 않는지 확인
4. A/B 각각 `/api/engine/start` 후 `/api/engine/status`가 계정별로 독립 동작하는지 확인

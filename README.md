# BTC Auto Trader

Upbit 기반 KRW 중심 자동매매 콘솔.  
Spring Boot 백엔드 + React 프론트 + PostgreSQL로 구성된 모노레포입니다.

---

## ✅ 핵심 기능
- 실시간 추천(거래대금/추세/변동성 기반)
- 모의계좌(Paper Trading) 포트폴리오/손익 표시
- 자동매매 설정(전략/리스크/선정 방식)

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

# Frontend redirect after login (path only 권장)
APP_AUTH_SUCCESS_REDIRECT_URL=/
APP_AUTH_FAILURE_REDIRECT_URL=/?loginError=true
APP_AUTH_DYNAMIC_REDIRECT_ENABLED=true
APP_AUTH_FRONTEND_PORT=5173

# Tenant owner account
APP_OWNER_EMAIL=juno980220@gmail.com

# Trading safety mode (default: owner account only)
APP_TRADING_OWNER_ONLY_MODE=true

# Exchange credential encryption key (required in production)
APP_EXCHANGE_KEY_ENCRYPTION_KEY=change-this-to-a-long-random-secret
```

### Local dev note
- Vite dev server는 `/api`, `/oauth2`, `/login` 경로를 백엔드(`:8080`)로 프록시합니다.
- `APP_OWNER_EMAIL` 계정은 기존 메인 DB를 사용하고, 신규 로그인 계정은 `btc_user_<user_id>` 형태의 전용 DB를 자동 생성합니다.
- `/api/engine/*`, `/api/order/*`, `/api/strategy/*`, `/api/portfolio/*`는 로그인 사용자 tenant DB 기준으로 동작합니다.
- 거래소 API 키는 사용자별로 암호화 저장되며(`user_exchange_credentials`), `/api/me/exchange-credentials`에서 관리합니다.

### Tenant 분리 확인 체크리스트
1. 계정 A/B 각각 로그인 후 `/api/me`의 `tenantDatabase`가 다른지 확인
2. A에서 `/api/strategy/markets` 변경 후 B에서 조회했을 때 값이 분리되는지 확인
3. A에서 주문 생성 후 B의 `/api/order/history`에 보이지 않는지 확인
4. A/B 각각 `/api/engine/start` 후 `/api/engine/status`가 계정별로 독립 동작하는지 확인

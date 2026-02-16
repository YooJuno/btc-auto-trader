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

# Frontend redirect after login
APP_AUTH_SUCCESS_REDIRECT_URL=http://localhost:5173/
APP_AUTH_FAILURE_REDIRECT_URL=http://localhost:5173/?loginError=true
APP_OWNER_EMAIL=juno980220@gmail.com
```

### Local dev note
- Vite dev server는 `/api`, `/oauth2`, `/login` 경로를 백엔드(`:8080`)로 프록시합니다.
- `APP_OWNER_EMAIL` 계정은 기존 메인 DB를 사용하고, 신규 로그인 계정은 `btc_user_<user_id>` 형태의 전용 DB를 자동 생성합니다.
- `/api/engine/*`, `/api/order/*`, `/api/strategy/*`, `/api/portfolio/*`는 로그인 사용자 tenant DB 기준으로 동작합니다.
